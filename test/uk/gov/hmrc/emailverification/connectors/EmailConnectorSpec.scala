/*
 * Copyright 2021 HM Revenue & Customs
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
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.emailverification.models.SendEmailRequest
import uk.gov.hmrc.gg.test.UnitSpec
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class EmailConnectorSpec extends UnitSpec with ScalaFutures {

  "send email" should {

    "submit request to email micro service to send email and get successful response status" in new Setup {
      when(mockServicesConfig.baseUrl(eqTo("email"))).thenReturn("emailHost://emailHost:1337")
      when(mockAppConfig.emailServicePath).thenReturn("/emailservicepath")

      // given
      val endpointUrl = "emailHost://emailHost:1337/emailservicepath/hmrc/email"
      val expectedRequestBody = SendEmailRequest(Seq(recipient), templateId, params)

      when(mockHttp.POST[SendEmailRequest, Either[UpstreamErrorResponse, HttpResponse]] (eqTo(endpointUrl), eqTo(expectedRequestBody), any)(any, any, any, any)
      ).thenReturn(Future.successful(Right(HttpResponse(202, ""))))

      // when
      val result = await(connector.sendEmail(recipient, templateId, params)(HeaderCarrier(), executionContext))

      // then
      result.status shouldBe 202
    }
  }

  sealed trait Setup {
    val hc = HeaderCarrier()
    val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    val mockHttp = mock[HttpClient]
    val params = Map("p1" -> "v1")
    val templateId = "my-template"
    val recipient = "user@example.com"
    val mockAppConfig = mock[AppConfig]
    val mockServicesConfig = mock[ServicesConfig]

    val connector = new EmailConnector(mockAppConfig, mockHttp, mockServicesConfig)
  }
}
