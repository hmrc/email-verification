/*
 * Copyright 2019 HM Revenue & Customs
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
import helpers.{TestSupport, LogCapturing}
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.LoneElement
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.libs.json.Writes
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.emailverification.models.{AnalyticsRequest, GaEvent}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class PlatformAnalyticsConnectorSpec extends TestSupport with MockitoSugar with LogCapturing with LoneElement with Eventually {

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
            (any(),any(),any())(any[Writes[AnalyticsRequest]], any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(response)

        noException should be thrownBy await(analyticsPlatformConnector.sendEvents(event))

        verify(httpMock).POST[AnalyticsRequest, HttpResponse](any(),any(), eqTo(Seq.empty))(any[Writes[AnalyticsRequest]], any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
      }
    }

    "swallow exceptions and log an error" in new Setup {
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        when(
          httpMock.POST[AnalyticsRequest, HttpResponse](any(),any(),any())(any[Writes[AnalyticsRequest]], any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future.failed(new RuntimeException("blow up")))

        noException should be thrownBy await(analyticsPlatformConnector.sendEvents(event))

        verify(httpMock).POST[AnalyticsRequest, HttpResponse](any(), any(), eqTo(Seq.empty))(any[Writes[AnalyticsRequest]], any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])

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
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

    val httpMock = mock[HttpClient]
    val environment = mock[Environment]
    val configuration = mock[Configuration]

    when(configuration.getString(any(),any())) thenReturn Some(aServiceUrl)
    when(configuration.getInt(any())) thenReturn Some(10)

    lazy val analyticsPlatformConnector = new PlatformAnalyticsConnector(httpMock, environment, configuration)
  }

}
