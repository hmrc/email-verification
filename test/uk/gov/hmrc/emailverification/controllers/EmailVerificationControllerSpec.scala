/*
 * Copyright 2020 HM Revenue & Customs
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
import org.joda.time.{DateTime, Period}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.emailverification.connectors.{EmailConnector, PlatformAnalyticsConnector}
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.emailverification.services.{AuditService, VerificationLinkService}
import uk.gov.hmrc.gg.test.UnitSpec
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}

class EmailVerificationControllerSpec extends UnitSpec {

  "requestVerification" should {
    "send email containing verificationLink param and return success response" in new Setup {
      val verificationLink = "verificationLink"
      when(mockVerifiedEmailRepo.isVerified(eqTo(recipient))).thenReturn(Future.successful(false))
      when(mockVerificationLinkService.verificationLinkFor(any, eqTo(ForwardUrl("http://some/url")))).thenReturn(verificationLink)
      when(mockEmailConnector.sendEmail(any, any, any)(any, any))
        .thenReturn(Future.successful(HttpResponse(202, "")))
      when(mockTokenRepo.upsert(any, any, any)).thenReturn(Future.unit)
      when(mockAppConfig.whitelistedDomains) thenReturn Set.empty[String]

      val result: Result = await(controller.requestVerification()(request.withBody(validRequest)))

      status(result) shouldBe Status.CREATED
      verify(mockTokenRepo).upsert(any, eqTo(recipient), eqTo(Period.days(2)))
      verify(mockEmailConnector).sendEmail(eqTo(recipient), eqTo(templateId), eqTo(params + ("verificationLink" -> verificationLink)))(any, any)
      val captor: ArgumentCaptor[GaEvent] = ArgumentCaptor.forClass(classOf[GaEvent])
      verify(mockAnalyticsConnector).sendEvents(captor.capture())(any, any)
      captor.getValue shouldBe GaEvents.verificationRequested

    }

    "return 400 if upstream email service returns bad request and should not create mongo entry" in new Setup {
      val verificationLink = "verificationLink"
      when(mockVerifiedEmailRepo.isVerified(eqTo(recipient))).thenReturn(Future.successful(false))
      when(mockVerificationLinkService.verificationLinkFor(any, eqTo(ForwardUrl("http://some/url")))).thenReturn(verificationLink)
      when(mockEmailConnector.sendEmail(any, any, any)(any, any))
        .thenReturn(Future.failed(UpstreamErrorResponse.apply("Bad Request from email", 400)))
      when(mockAppConfig.whitelistedDomains) thenReturn Set.empty[String]

      val result: Result = await(controller.requestVerification()(request.withBody(validRequest)))

      status(result) shouldBe Status.BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"
      verify(mockEmailConnector).sendEmail(eqTo(recipient), eqTo(templateId), eqTo(params + ("verificationLink" -> verificationLink)))(any, any)
      verify(mockAuditConnector).sendExtendedEvent(any)(any, any)
      verify(mockAnalyticsConnector).sendEvents(any)(any, any)
      verifyNoInteractions(mockTokenRepo)
    }

    "return 409 when email already registered" in new Setup {
      when(mockVerifiedEmailRepo.isVerified(eqTo(recipient))).thenReturn(Future.successful(true))
      when(mockAppConfig.whitelistedDomains) thenReturn Set.empty[String]

      val result: Result = await(controller.requestVerification()(request.withBody(validRequest)))
      status(result) shouldBe Status.CONFLICT
    }
  }

  "validateToken" should {
    "return 201 when the token is valid" in new Setup {
      when(mockTokenRepo.findToken(eqTo(someToken))).thenReturn(Future.successful(Some(VerificationDoc(email, someToken, DateTime.now()))))
      when(mockVerifiedEmailRepo.insert(eqTo(email))).thenReturn(Future.unit)
      when(mockVerifiedEmailRepo.find(eqTo(email))).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.validateToken()(request.withBody(Json.obj("token" -> someToken)))

      status(result) shouldBe Status.CREATED
      verify(mockVerifiedEmailRepo).insert(eqTo(email))
      verify(mockVerifiedEmailRepo).find(eqTo(email))
      val captor: ArgumentCaptor[GaEvent] = ArgumentCaptor.forClass(classOf[GaEvent])
      verify(mockAnalyticsConnector).sendEvents(captor.capture())(any, any)
      captor.getValue shouldBe GaEvents.verificationSuccess
    }

    "return 204 when the token is valid and the email was already validated" in new Setup {
      when(mockTokenRepo.findToken(eqTo(someToken))).thenReturn(Future.successful(Some(VerificationDoc(email, someToken, DateTime.now()))))
      when(mockVerifiedEmailRepo.find(eqTo(email))).thenReturn(Future.successful(Some(VerifiedEmail(email))))

      val result: Future[Result] = controller.validateToken()(request.withBody(Json.obj("token" -> someToken)))

      status(result) shouldBe Status.NO_CONTENT
      verify(mockVerifiedEmailRepo).find(eqTo(email))
      verifyNoMoreInteractions(mockVerifiedEmailRepo)
      val captor: ArgumentCaptor[GaEvent] = ArgumentCaptor.forClass(classOf[GaEvent])
      verify(mockAnalyticsConnector).sendEvents(captor.capture())(any, any)
      captor.getValue shouldBe GaEvents.verificationSuccess
    }

    "return 400 when the token does not exist in mongo" in new Setup {
      when(mockTokenRepo.findToken(eqTo(someToken))).thenReturn(Future.successful(None))

      val result: Result = await(controller.validateToken()(request.withBody(Json.obj("token" -> someToken))))

      status(result) shouldBe Status.BAD_REQUEST
      val captor: ArgumentCaptor[GaEvent] = ArgumentCaptor.forClass(classOf[GaEvent])
      verify(mockAnalyticsConnector).sendEvents(captor.capture())(any, any)
      captor.getValue shouldBe GaEvents.verificationFailed
      verifyNoInteractions(mockVerifiedEmailRepo)
    }
  }

  "verifiedEmail" should {
    "return 200 with verified email in the body" in new Setup {
      when(mockVerifiedEmailRepo.find(eqTo(email))).thenReturn(Future.successful(Some(VerifiedEmail(email))))
      val response: Future[Result] = controller.verifiedEmail()(request.withBody(Json.obj("email" -> email)))
      status(response) shouldBe 200
      contentAsJson(response).as[VerifiedEmail] shouldBe VerifiedEmail(email)
      verify(mockVerifiedEmailRepo).find(eqTo(email))
      verifyNoMoreInteractions(mockVerifiedEmailRepo)
      verify(mockAuditService).sendCheckEmailVerifiedEvent(*, *, eqTo(OK))(*)
    }

    "return 404 if email not found" in new Setup {
      when(mockVerifiedEmailRepo.find(eqTo(email))).thenReturn(Future.successful(None))
      val response: Future[Result] = controller.verifiedEmail()(request.withBody(Json.obj("email" -> email)))
      status(response) shouldBe 404
      verify(mockVerifiedEmailRepo).find(eqTo(email))
      verifyNoMoreInteractions(mockVerifiedEmailRepo)
      verify(mockAuditService).sendCheckEmailVerifiedEvent(*, *, eqTo(NOT_FOUND))(*)
    }
  }

  trait Setup {
    val mockEmailConnector = mock[EmailConnector]
    val mockVerificationLinkService = mock[VerificationLinkService]
    val mockTokenRepo = mock[VerificationTokenMongoRepository]
    val mockVerifiedEmailRepo = mock[VerifiedEmailMongoRepository]
    val mockAnalyticsConnector = mock[PlatformAnalyticsConnector]
    val someToken = "some-token"
    val request = FakeRequest()
    val mockAppConfig = mock[AppConfig]
    val mockAuditConnector = mock[AuditConnector]
    val mockAuditService = mock[AuditService]

    val controller = new EmailVerificationController(
      mockEmailConnector,
      mockVerificationLinkService,
      mockTokenRepo,
      mockVerifiedEmailRepo,
      mockAnalyticsConnector,
      mockAuditConnector,
      mockAuditService,
      stubControllerComponents()
    )(ExecutionContext.global, mockAppConfig)
    val templateId = "my-template"
    val recipient = "user@example.com"
    val params = Map("name" -> "Mr Joe Bloggs")
    val paramsJsonStr = Json.toJson(params).toString()
    val email = "user@email.com"

    val validRequest: JsValue = Json.parse(
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
