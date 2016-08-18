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

package uk.gov.hmrc.emailverification.controllers

import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.emailverification.MockitoSugarRush
import uk.gov.hmrc.emailverification.connectors.EmailConnector
import uk.gov.hmrc.emailverification.services.VerificationLinkService
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class EmailVerificationControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugarRush with ScalaFutures {

  "requestVerification" should {
    "send email containing verificationLink param and return 204" in new Setup {
      when(emailConnectorMock.sendEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(HttpResponse(202)))

      val verificationLink = "verificationLink"

      when(verificationLinkServiceMock.createVericationLink()).thenReturn(verificationLink)

      val result = await(underTest.requestVerification()(FakeRequest().withBody(validRequest)))

      verify(emailConnectorMock).sendEmail(eqTo(recipient), eqTo(templateId), eqTo(params + ("verificationLink" -> verificationLink)))(any[HeaderCarrier])

      status(result) shouldBe Status.NO_CONTENT

    }
  }

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val emailConnectorMock: EmailConnector = mock[EmailConnector]
    val verificationLinkServiceMock: VerificationLinkService = mock[VerificationLinkService]
    val underTest = new EmailVerificationController {
      override val emailConnector = emailConnectorMock
      override val verificationLinkService  = verificationLinkServiceMock
    }

    val templateId = "my-template"
    val recipient = "user@example.com"
    val params = Map("name2" -> "Mr Joe Bloggs")
    val paramsJsonStr = Json.toJson(params).toString()


    val validRequest = Json.parse(
      s"""{
          |  "email": "$recipient",
          |  "templateId": "$templateId",
          |  "templateParameters": $paramsJsonStr,
          |  "linkExpiryDuration" : "P2D",
          |  "continueUrl" : "http://some/url"
          |}""".stripMargin
    )

  }


}
