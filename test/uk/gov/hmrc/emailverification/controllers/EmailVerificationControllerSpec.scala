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

import org.joda.time.Period
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.emailverification.MockitoSugarRush
import uk.gov.hmrc.emailverification.connectors.EmailConnector
import uk.gov.hmrc.emailverification.repositories.VerificationTokenMongoRepository
import uk.gov.hmrc.emailverification.services.VerificationLinkService
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class EmailVerificationControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugarRush with ScalaFutures {

  "requestVerification" should {
    "send email containing verificationLink param and return 204" in new Setup {
      val verificationLink = "verificationLink"
      when(verificationLinkServiceMock.verificationLinkFor(token, "http://some/url")).thenReturn(verificationLink)
      when(emailConnectorMock.sendEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(HttpResponse(202)))
      when(tokenRepoMock.insert(any(), any(), any())(any())).thenReturn(Future.successful(mock[WriteResult]))

      val result = await(controller.requestVerification()(FakeRequest().withBody(validRequest)))

      status(result) shouldBe Status.NO_CONTENT
      verify(tokenRepoMock).insert(token, recipient, Period.days(2))
      verify(emailConnectorMock).sendEmail(recipient, templateId, params + ("verificationLink" -> verificationLink))
    }


    "blow up when mongo fails" in new Setup {
      when(tokenRepoMock.insert(any(), any(), any())(any())).thenReturn(Future.failed(new RuntimeException("lp0 on fire !!!")))

      intercept[Exception] {
        await(controller.requestVerification()(FakeRequest().withBody(validRequest)))
      }

      verify(tokenRepoMock).insert(token, recipient, Period.days(2))
      verifyZeroInteractions(emailConnectorMock, verificationLinkServiceMock)
    }
  }

  trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    val emailConnectorMock: EmailConnector = mock[EmailConnector]
    val verificationLinkServiceMock: VerificationLinkService = mock[VerificationLinkService]
    val tokenRepoMock: VerificationTokenMongoRepository = mock[VerificationTokenMongoRepository]
    val controller = new EmailVerificationController {
      override val emailConnector = emailConnectorMock
      override val verificationLinkService = verificationLinkServiceMock
      override val tokenRepo = tokenRepoMock
      override def newToken = token
      override implicit def hc(implicit rh: RequestHeader) = headerCarrier
    }
    val token = "theToken"
    val templateId = "my-template"
    val recipient = "user@example.com"
    val params = Map("name" -> "Mr Joe Bloggs")
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
