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

import helpers.MaterializerSupport
import org.joda.time.{DateTime, Period}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import reactivemongo.api.commands.{DefaultWriteResult, WriteResult}
import uk.gov.hmrc.emailverification.MockitoSugarRush
import uk.gov.hmrc.emailverification.connectors.{EmailConnector, GaEvents, PlatformAnalyticsConnector}
import uk.gov.hmrc.emailverification.repositories.{VerificationDoc, VerificationTokenMongoRepository, VerifiedEmail, VerifiedEmailMongoRepository}
import uk.gov.hmrc.emailverification.services.VerificationLinkService
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class EmailVerificationControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugarRush with ScalaFutures with MaterializerSupport {

  "requestVerification" should {
    "send email containing verificationLink param and return success response" in new Setup {
      val verificationLink = "verificationLink"
      when(verifiedEmailRepoMock.isVerified(recipient)).thenReturn(Future.successful(false))
      when(verificationLinkServiceMock.verificationLinkFor(token, "http://some/url")).thenReturn(verificationLink)
      when(emailConnectorMock.sendEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(HttpResponse(202)))
      when(tokenRepoMock.upsert(any(), any(), any())(any())).thenReturn(writeResult)

      val result = await(controller.requestVerification()(request.withBody(validRequest)))

      status(result) shouldBe Status.CREATED
      verify(tokenRepoMock).upsert(token, recipient, Period.days(2))
      verify(emailConnectorMock).sendEmail(recipient, templateId, params + ("verificationLink" -> verificationLink))
      verify(analyticsConnectorMock).sendEvents(GaEvents.verificationRequested)
    }

    "return 400 if upstream email service returns bad request and should not create mongo entry" in new Setup {
      val verificationLink = "verificationLink"
      when(verifiedEmailRepoMock.isVerified(recipient)).thenReturn(Future.successful(false))
      when(verificationLinkServiceMock.verificationLinkFor(token, "http://some/url")).thenReturn(verificationLink)
      when(emailConnectorMock.sendEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.failed(new BadRequestException("Bad Request from email")))
      when(tokenRepoMock.upsert(any(), any(), any())(any())).thenReturn(writeResult)

      val result = await(controller.requestVerification()(request.withBody(validRequest)))

      status(result) shouldBe Status.BAD_REQUEST
      (jsonBodyOf(result) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"
      verify(emailConnectorMock).sendEmail(recipient, templateId, params + ("verificationLink" -> verificationLink))
      verifyZeroInteractions(tokenRepoMock)
    }

    "return 409 when email already registered" in new Setup {
      when(verifiedEmailRepoMock.isVerified(recipient)).thenReturn(Future.successful(true))

      val result = await(controller.requestVerification()(request.withBody(validRequest)))
      status(result) shouldBe Status.CONFLICT
    }
  }

  "validateToken" should {
    "return 201 when the token is valid" in new Setup {
      when(tokenRepoMock.findToken(someToken)).thenReturn(Future.successful(Some(VerificationDoc(email, someToken, DateTime.now()))))
      when(verifiedEmailRepoMock.insert(email)).thenReturn(writeResult)
      when(verifiedEmailRepoMock.find(email)).thenReturn(Future.successful(None))

      val result = await(controller.validateToken()(request.withBody(Json.obj("token" -> someToken))))

      status(result) shouldBe Status.CREATED
      verify(verifiedEmailRepoMock).insert(email)
      verify(verifiedEmailRepoMock).find(email)
      verify(analyticsConnectorMock).sendEvents(GaEvents.verificationSuccess)
    }

    "return 204 when the token is valid and the email was already validated" in new Setup {
      when(tokenRepoMock.findToken(someToken)).thenReturn(Future.successful(Some(VerificationDoc(email, someToken, DateTime.now()))))
      when(verifiedEmailRepoMock.find(email)).thenReturn(Future.successful(Some(VerifiedEmail(email))))

      val result = await(controller.validateToken()(request.withBody(Json.obj("token" -> someToken))))

      status(result) shouldBe Status.NO_CONTENT
      verify(verifiedEmailRepoMock).find(email)
      verifyNoMoreInteractions(verifiedEmailRepoMock)
      verify(analyticsConnectorMock).sendEvents(GaEvents.verificationSuccess)
    }

    "return 400 when the token does not exist in mongo" in new Setup {
      when(tokenRepoMock.findToken(someToken)).thenReturn(Future.successful(None))

      val result = await(controller.validateToken()(request.withBody(Json.obj("token" -> someToken))))

      status(result) shouldBe Status.BAD_REQUEST
      verify(analyticsConnectorMock).sendEvents(GaEvents.verificationFailed)
      verifyZeroInteractions(verifiedEmailRepoMock)
    }
  }

  "verifiedEmail" should {
    "return 200 with verified email in the body" in new Setup {
      when(verifiedEmailRepoMock.find(email)).thenReturn(Future.successful(Some(VerifiedEmail(email))))
      val response = controller.verifiedEmail(email)(request).futureValue
      status(response) shouldBe 200
      jsonBodyOf(response).as[VerifiedEmail] shouldBe VerifiedEmail(email)
      verify(verifiedEmailRepoMock).find(email)
      verifyNoMoreInteractions(verifiedEmailRepoMock)
    }

    "return 404 if email not found" in new Setup {
      when(verifiedEmailRepoMock.find(email)).thenReturn(Future.successful(None))
      val response = controller.verifiedEmail(email)(request).futureValue
      status(response) shouldBe 404
      verify(verifiedEmailRepoMock).find(email)
      verifyNoMoreInteractions(verifiedEmailRepoMock)
    }
  }

  trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    val emailConnectorMock: EmailConnector = mock[EmailConnector]
    val verificationLinkServiceMock: VerificationLinkService = mock[VerificationLinkService]
    val tokenRepoMock: VerificationTokenMongoRepository = mock[VerificationTokenMongoRepository]
    val verifiedEmailRepoMock: VerifiedEmailMongoRepository = mock[VerifiedEmailMongoRepository]
    val analyticsConnectorMock: PlatformAnalyticsConnector = mock[PlatformAnalyticsConnector]
    val someToken = "some-token"
    val request = FakeRequest()

    when(analyticsConnectorMock.sendEvents(any())(any())).thenReturn(Future.successful(()))

    val controller = new EmailVerificationController {
      override val emailConnector = emailConnectorMock
      override val verificationLinkService = verificationLinkServiceMock
      override val tokenRepo = tokenRepoMock

      override def newToken() = token

      override def verifiedEmailRepo = verifiedEmailRepoMock

      override def analyticsConnector = analyticsConnectorMock

      override implicit def hc(implicit rh: RequestHeader) = headerCarrier
    }
    val token = "theToken"
    val templateId = "my-template"
    val recipient = "user@example.com"
    val params = Map("name" -> "Mr Joe Bloggs")
    val paramsJsonStr = Json.toJson(params).toString()
    val email = "user@email.com"
    val writeResult: Future[WriteResult] = Future.successful(DefaultWriteResult(true, 0, Seq.empty, None, None, None))

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
