/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.emailverification.connectors


import ch.qos.logback.classic.Level
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.LoneElement
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.Logger
import play.api.libs.json.Writes
import uk.gov.hmrc.play.http.ws.WSPost
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}

import scala.concurrent.Future

class PlatformAnalyticsConnectorSpec extends UnitSpec with MockitoSugar with LogCapturing with LoneElement with Eventually {

  "sendEvents" should {

    val scenarios = Table(
      ("scenario", "response"),
      ("response is successful", Future.successful(mock[HttpResponse])),
      ("response status different from 2xx", Future.failed(new RuntimeException()))
    )

    forAll(scenarios) { (scenario: String, response: Future[HttpResponse]) =>
      s"send a GA event to platform-analytics - $scenario" in new Setup {
        when(
          httpMock.POST[AnalyticsRequest, HttpResponse]
            (eqTo(s"$aServiceUrl/platform-analytics/event"), eqTo(data), any[Seq[(String, String)]])(any[Writes[AnalyticsRequest]], any[HttpReads[HttpResponse]], any[HeaderCarrier])
        ).thenReturn(response)

        noException should be thrownBy await(analyticsPlatformConnector.sendEvents(event))

        verify(httpMock).POST[AnalyticsRequest, HttpResponse](eqTo(s"$aServiceUrl/platform-analytics/event"), eqTo(data), eqTo(Seq.empty))(any[Writes[AnalyticsRequest]], any[HttpReads[HttpResponse]], any[HeaderCarrier])
      }
    }

    "swallow exceptions and log an error" in new Setup {
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        when(
          httpMock.POST[AnalyticsRequest, HttpResponse](eqTo(s"$aServiceUrl/platform-analytics/event"), eqTo(data), any[Seq[(String, String)]])(any[Writes[AnalyticsRequest]], any[HttpReads[HttpResponse]], any[HeaderCarrier])
        ).thenReturn(Future.failed(new RuntimeException("blow up")))

        noException should be thrownBy await(analyticsPlatformConnector.sendEvents(event))

        verify(httpMock).POST[AnalyticsRequest, HttpResponse](eqTo(s"$aServiceUrl/platform-analytics/event"), eqTo(data), eqTo(Seq.empty))(any[Writes[AnalyticsRequest]], any[HttpReads[HttpResponse]], any[HeaderCarrier])

        eventually {
          logEvents.filter(_.getLevel == Level.ERROR).loneElement.getMessage should include(s"Couldn't send analytics event")
        }
      }
    }
  }

  trait Setup {
    val event = GaEvent("", "", "", Seq.empty, None)
    val data = AnalyticsRequest("uuid", Seq(event))
    val aServiceUrl = "service-url"
    implicit val hc = HeaderCarrier()

    val httpMock = mock[WSPost]
    val analyticsPlatformConnector = new PlatformAnalyticsConnector {
      override val serviceUrl = aServiceUrl
      override val http = httpMock
      override def gaClientId = "uuid"
    }
  }

}
