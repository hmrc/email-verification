/*
 * Copyright 2016 HM Revenue & Customs
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

import config.AppConfig
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.emailverification.MockitoSugarRush
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class EmailConnectorSpec extends UnitSpec with MockitoSugarRush with ScalaFutures {

  "requesting a token" should {

    "return a valid token when logging in with a valid username and password (empty path)" in new Setup {
      when(appConfigMock.path).thenReturn("")
      val sendEmailUrl = s"$url/send-templated-email"
      when(httpMock.POST[SendEmailRequest, HttpResponse](eqTo(sendEmailUrl), any(), any())(any(), any(), any[HeaderCarrier])).thenReturn(Future.successful(HttpResponse(202)))

      val result = await(connector.sendEmail(recipient, templateId, params))

      verify(httpMock).POST(eqTo(sendEmailUrl), eqTo(SendEmailRequest(Seq(recipient), templateId, params)), any())(any(), any(), any[HeaderCarrier])
    }

    "return a valid token when logging in with a valid username and password (with path to stub)" in new Setup {
      when(appConfigMock.path).thenReturn("/some-path-to-stub")
      val sendEmailUrl = s"$url/some-path-to-stub/send-templated-email"
      when(httpMock.POST[SendEmailRequest, HttpResponse](eqTo(sendEmailUrl), any(), any())(any(), any(), any[HeaderCarrier])).thenReturn(Future.successful(HttpResponse(202)))

      val result = await(connector.sendEmail(recipient, templateId, params))

      verify(httpMock).POST(eqTo(sendEmailUrl), eqTo(SendEmailRequest(Seq(recipient), templateId, params)), any())(any(), any(), any[HeaderCarrier])
    }
  }

  sealed trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val httpMock: WSHttp = mock[WSHttp]
    val appConfigMock: AppConfig = mock[AppConfig]
    val url = "http://somewhere"
    val params = Map("p1" -> "v1")
    val templateId = "my-template"
    val recipient = "user@example.com"

    val connector = new EmailConnector {
      override val config = appConfigMock

      override val http = httpMock

      override val serviceUrl = url
    }
  }

}
