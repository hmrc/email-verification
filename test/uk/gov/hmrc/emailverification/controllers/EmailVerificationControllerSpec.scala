/*
 * Copyright 2023 HM Revenue & Customs
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

import config.AppConfig
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.Mockito._
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.emailverification.connectors.EmailConnector
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.VerificationTokenMongoRepository
import uk.gov.hmrc.emailverification.services.{AuditService, VerificationLinkService, VerifiedEmailService}
import uk.gov.hmrc.gg.test.UnitSpec
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}

@annotation.nowarn("msg=deprecated")
class EmailVerificationControllerSpec extends UnitSpec {
  "requestVerification" should {
    "send email containing verificationLink param and return success response" in new Setup {
      val verificationLink = "verificationLink"
      when(mockVerifiedEmailService.isVerified(eqTo(emailMixedCase))).thenReturn(Future.successful(false))
      when(mockVerificationLinkService.verificationLinkFor(any, eqTo(ForwardUrl("http://some/url")))).thenReturn(verificationLink)
      when(mockEmailConnector.sendEmail(any, any, any)(any, any))
        .thenReturn(Future.successful(HttpResponse(202, "")))
      when(mockTokenRepo.upsert(any, any, any, any)).thenReturn(Future.unit)

      val result: Result = await(controller.requestVerification()(request.withBody(validRequest)))

      status(result) shouldBe Status.CREATED
      verify(mockTokenRepo).upsert(any, eqTo(emailMixedCase), eqTo(Duration.ofDays(2)), any)
      verify(mockEmailConnector).sendEmail(eqTo(emailMixedCase), eqTo(templateId), eqTo(params + ("verificationLink" -> verificationLink)))(any, any)
    }

    "return 400 if upstream email service returns bad request and should not create mongo entry" in new Setup {
      val verificationLink = "verificationLink"
      when(mockVerifiedEmailService.isVerified(eqTo(emailMixedCase))).thenReturn(Future.successful(false))
      when(mockVerificationLinkService.verificationLinkFor(any, eqTo(ForwardUrl("http://some/url")))).thenReturn(verificationLink)
      when(mockEmailConnector.sendEmail(any, any, any)(any, any))
        .thenReturn(Future.failed(UpstreamErrorResponse.apply("Bad Request from email", 400)))

      val result: Result = await(controller.requestVerification()(request.withBody(validRequest)))

      status(result)                              shouldBe Status.BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"
      verify(mockEmailConnector).sendEmail(eqTo(emailMixedCase), eqTo(templateId), eqTo(params + ("verificationLink" -> verificationLink)))(any, any)
      verify(mockAuditConnector).sendExtendedEvent(any)(any, any)
      verifyNoInteractions(mockTokenRepo)
    }

    "return 409 when email already registered" in new Setup {
      when(mockVerifiedEmailService.isVerified(eqTo(emailMixedCase))).thenReturn(Future.successful(true))

      val result: Result = await(controller.requestVerification()(request.withBody(validRequest)))
      status(result) shouldBe Status.CONFLICT
    }
  }

  "validateToken" should {
    "return 201 when the token is valid" in new Setup {
      when {
        mockTokenRepo.findToken(eqTo(someToken))
      } thenReturn Future.successful(Some(VerificationDoc(emailMixedCase, someToken, Instant.now)))
      when(mockVerifiedEmailService.insert(eqTo(emailMixedCase))) thenReturn Future.unit
      when(mockVerifiedEmailService.find(eqTo(emailMixedCase))) thenReturn Future.successful(None)

      val result: Future[Result] = controller.validateToken()(request.withBody(Json.obj("token" -> someToken)))

      status(result) shouldBe Status.CREATED
      verify(mockVerifiedEmailService).insert(eqTo(emailMixedCase))
      verify(mockVerifiedEmailService).find(eqTo(emailMixedCase))
    }

    "return 204 when the token is valid and the email was already validated" in new Setup {
      when(mockTokenRepo.findToken(eqTo(someToken))).thenReturn(Future.successful(Some(VerificationDoc(emailMixedCase, someToken, Instant.now))))
      when(mockVerifiedEmailService.find(eqTo(emailMixedCase))).thenReturn(Future.successful(Some(VerifiedEmail(emailMixedCase))))

      val result: Future[Result] = controller.validateToken()(request.withBody(Json.obj("token" -> someToken)))

      status(result) shouldBe Status.NO_CONTENT
      verify(mockVerifiedEmailService).find(eqTo(emailMixedCase))
      verifyNoMoreInteractions(mockVerifiedEmailService)
    }

    "return 400 when the token does not exist in mongo" in new Setup {
      when(mockTokenRepo.findToken(eqTo(someToken))).thenReturn(Future.successful(None))

      val result: Result = await(controller.validateToken()(request.withBody(Json.obj("token" -> someToken))))

      status(result) shouldBe Status.BAD_REQUEST
      verifyNoInteractions(mockVerifiedEmailService)
    }
  }

  "verifiedEmail" should {
    "return 200 with verified email in the body" in new Setup {
      when(mockVerifiedEmailService.find(eqTo(emailMixedCase))).thenReturn(Future.successful(Some(VerifiedEmail(emailMixedCase))))
      val response: Future[Result] = controller.verifiedEmail()(request.withBody(Json.obj("email" -> emailMixedCase)))
      status(response)                          shouldBe 200
      contentAsJson(response).as[VerifiedEmail] shouldBe VerifiedEmail(emailMixedCase)
      verify(mockVerifiedEmailService).find(eqTo(emailMixedCase))
      verifyNoMoreInteractions(mockVerifiedEmailService)
      verify(mockAuditService).sendCheckEmailVerifiedEvent(any, any, eqTo(OK))(any)
    }

    "lower case email address" in new Setup {
      when(mockVerifiedEmailService.find(eqTo(emailMixedCase.toUpperCase))).thenReturn(Future.successful(Some(VerifiedEmail(emailMixedCase))))
      val response: Future[Result] = controller.verifiedEmail()(request.withBody(Json.obj("email" -> emailMixedCase.toUpperCase)))
      status(response)                          shouldBe 200
      contentAsJson(response).as[VerifiedEmail] shouldBe VerifiedEmail(emailMixedCase)
      verify(mockVerifiedEmailService).find(eqTo(emailMixedCase.toUpperCase))
      verifyNoMoreInteractions(mockVerifiedEmailService)
      verify(mockAuditService).sendCheckEmailVerifiedEvent(any, any, eqTo(OK))(any)
    }

    "return 404 if email not found" in new Setup {
      when(mockVerifiedEmailService.find(eqTo(emailMixedCase))).thenReturn(Future.successful(None))
      val response: Future[Result] = controller.verifiedEmail()(request.withBody(Json.obj("email" -> emailMixedCase)))
      status(response) shouldBe 404
      verify(mockVerifiedEmailService).find(eqTo(emailMixedCase))
      verifyNoMoreInteractions(mockVerifiedEmailService)
      verify(mockAuditService).sendCheckEmailVerifiedEvent(any, any, eqTo(NOT_FOUND))(any)
    }
  }

  trait Setup {
    val mockEmailConnector: EmailConnector = mock[EmailConnector]
    val mockVerificationLinkService: VerificationLinkService = mock[VerificationLinkService]
    val mockTokenRepo: VerificationTokenMongoRepository = mock[VerificationTokenMongoRepository]
    val mockVerifiedEmailService: VerifiedEmailService = mock[VerifiedEmailService]
    val someToken = "some-token"
    val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    val mockAppConfig: AppConfig = mock[AppConfig]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockAuditService: AuditService = mock[AuditService]

    val controller = new EmailVerificationController(
      mockEmailConnector,
      mockVerificationLinkService,
      mockTokenRepo,
      mockVerifiedEmailService,
      mockAuditConnector,
      mockAuditService,
      stubControllerComponents()
    )(ExecutionContext.global, mockAppConfig)
    val templateId = "my-template"
    val emailMixedCase = "uSeR@eXamPle.com"
    val emailLowerCase = "user@example.com"
    val params: Map[String, String] = Map("name" -> "Mr Joe Bloggs")
    val paramsJsonStr: String = Json.toJson(params).toString()

    val validRequest: JsValue = Json.parse(
      s"""{
         |  "email": "$emailMixedCase",
         |  "templateId": "$templateId",
         |  "templateParameters": $paramsJsonStr,
         |  "linkExpiryDuration" : "P2D",
         |  "continueUrl" : "http://some/url"
         |}""".stripMargin
    )
  }

}
