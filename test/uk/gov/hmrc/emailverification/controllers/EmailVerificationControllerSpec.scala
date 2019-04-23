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

package uk.gov.hmrc.emailverification.controllers

import config.AppConfig
import helpers.MaterializerSupport
import org.joda.time.{DateTime, Period}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq ⇒ EQ, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import reactivemongo.api.commands.{DefaultWriteResult, WriteResult}
import uk.gov.hmrc.emailverification.MockitoSugarRush
import uk.gov.hmrc.emailverification.connectors.{EmailConnector, PlatformAnalyticsConnector}
import uk.gov.hmrc.emailverification.models.{ForwardUrl, GaEvent, GaEvents, VerificationDoc, VerifiedEmail}
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.emailverification.services.VerificationLinkService
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class EmailVerificationControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugarRush with ScalaFutures with MaterializerSupport {

  "requestVerification" should {
    "send email containing verificationLink param and return success response" in new Setup {
      val verificationLink = "verificationLink"
      when(verifiedEmailRepoMock.isVerified(EQ(recipient))(any())).thenReturn(Future.successful(false))
      when(verificationLinkServiceMock.verificationLinkFor(token, ForwardUrl("http://some/url"))).thenReturn(verificationLink)
      when(emailConnectorMock.sendEmail(any(), any(), any())(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(202)))
      when(tokenRepoMock.upsert(any(), any(), any())(any())).thenReturn(writeResult)
      when(appConfigMock.whitelistedDomains) thenReturn Set.empty[String]

      val result = await(controller.requestVerification()(request.withBody(validRequest)))

      status(result) shouldBe Status.CREATED
      verify(tokenRepoMock).upsert(EQ(token), EQ(recipient), EQ(Period.days(2)))(any[HeaderCarrier])
      verify(emailConnectorMock).sendEmail(EQ(recipient), EQ(templateId), EQ(params + ("verificationLink" -> verificationLink)))(any[HeaderCarrier],any[ExecutionContext])
      val captor: ArgumentCaptor[Seq[GaEvent]] = ArgumentCaptor.forClass(classOf[Seq[GaEvent]])
      verify(analyticsConnectorMock).sendEvents(captor.capture():_*)(any[HeaderCarrier],any[ExecutionContext])
      captor.getValue.head shouldBe GaEvents.verificationRequested
    }

    "return 400 if upstream email service returns bad request and should not create mongo entry" in new Setup {
      val verificationLink = "verificationLink"
      when(verifiedEmailRepoMock.isVerified(EQ(recipient))(any())).thenReturn(Future.successful(false))
      when(verificationLinkServiceMock.verificationLinkFor(token, ForwardUrl("http://some/url"))).thenReturn(verificationLink)
      when(emailConnectorMock.sendEmail(any(), any(), any())(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new BadRequestException("Bad Request from email")))
      when(tokenRepoMock.upsert(any(), any(), any())(any())).thenReturn(writeResult)
      when(appConfigMock.whitelistedDomains) thenReturn Set.empty[String]

      val result = await(controller.requestVerification()(request.withBody(validRequest)))

      status(result) shouldBe Status.BAD_REQUEST
      (jsonBodyOf(result) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"
      verify(emailConnectorMock).sendEmail(EQ(recipient), EQ(templateId), EQ(params + ("verificationLink" -> verificationLink)))(any[HeaderCarrier],any[ExecutionContext])
      verifyZeroInteractions(tokenRepoMock)
    }

    "return 409 when email already registered" in new Setup {
      when(verifiedEmailRepoMock.isVerified(EQ(recipient))(any())).thenReturn(Future.successful(true))
      when(appConfigMock.whitelistedDomains) thenReturn Set.empty[String]

      val result = await(controller.requestVerification()(request.withBody(validRequest)))
      status(result) shouldBe Status.CONFLICT
    }
  }

  "validateToken" should {
    "return 201 when the token is valid" in new Setup {
      when(tokenRepoMock.findToken(EQ(someToken))(any())).thenReturn(Future.successful(Some(VerificationDoc(email, someToken, DateTime.now()))))
      when(verifiedEmailRepoMock.insert(EQ(email))(any())).thenReturn(writeResult)
      when(verifiedEmailRepoMock.find(EQ(email))(any())).thenReturn(Future.successful(None))

      val result = await(controller.validateToken()(request.withBody(Json.obj("token" -> someToken))))

      status(result) shouldBe Status.CREATED
      verify(verifiedEmailRepoMock).insert(EQ(email))(any())
      verify(verifiedEmailRepoMock).find(EQ(email))(any())
      val captor: ArgumentCaptor[Seq[GaEvent]] = ArgumentCaptor.forClass(classOf[Seq[GaEvent]])
      verify(analyticsConnectorMock).sendEvents(captor.capture():_*)(any[HeaderCarrier],any[ExecutionContext])
      captor.getValue.head shouldBe GaEvents.verificationSuccess
    }

    "return 204 when the token is valid and the email was already validated" in new Setup {
      when(tokenRepoMock.findToken(EQ(someToken))(any())).thenReturn(Future.successful(Some(VerificationDoc(email, someToken, DateTime.now()))))
      when(verifiedEmailRepoMock.find(EQ(email))(any())).thenReturn(Future.successful(Some(VerifiedEmail(email))))

      val result = await(controller.validateToken()(request.withBody(Json.obj("token" -> someToken))))

      status(result) shouldBe Status.NO_CONTENT
      verify(verifiedEmailRepoMock).find(EQ(email))(any())
      verifyNoMoreInteractions(verifiedEmailRepoMock)
      val captor: ArgumentCaptor[Seq[GaEvent]] = ArgumentCaptor.forClass(classOf[Seq[GaEvent]])
      verify(analyticsConnectorMock).sendEvents(captor.capture():_*)(any[HeaderCarrier],any[ExecutionContext])
      captor.getValue.head shouldBe GaEvents.verificationSuccess
    }

    "return 400 when the token does not exist in mongo" in new Setup {
      when(tokenRepoMock.findToken(EQ(someToken))(any())).thenReturn(Future.successful(None))

      val result = await(controller.validateToken()(request.withBody(Json.obj("token" -> someToken))))

      status(result) shouldBe Status.BAD_REQUEST
      val captor: ArgumentCaptor[Seq[GaEvent]] = ArgumentCaptor.forClass(classOf[Seq[GaEvent]])
      verify(analyticsConnectorMock).sendEvents(captor.capture():_*)(any[HeaderCarrier],any[ExecutionContext])
      captor.getValue.head shouldBe GaEvents.verificationFailed
      verifyZeroInteractions(verifiedEmailRepoMock)
    }
  }

  "verifiedEmail" should {
    "return 200 with verified email in the body" in new Setup {
      when(verifiedEmailRepoMock.find(EQ(email))(any())).thenReturn(Future.successful(Some(VerifiedEmail(email))))
      val response = controller.verifiedEmail(email)(request).futureValue
      status(response) shouldBe 200
      jsonBodyOf(response).as[VerifiedEmail] shouldBe VerifiedEmail(email)
      verify(verifiedEmailRepoMock).find(EQ(email))(any())
      verifyNoMoreInteractions(verifiedEmailRepoMock)
    }

    "return 404 if email not found" in new Setup {
      when(verifiedEmailRepoMock.find(EQ(email))(any())).thenReturn(Future.successful(None))
      val response = controller.verifiedEmail(email)(request).futureValue
      status(response) shouldBe 404
      verify(verifiedEmailRepoMock).find(EQ(email))(any())
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
    implicit val testMdcLoggingExecutionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val appConfigMock : AppConfig = mock[AppConfig]
    val auditConnector = mock[AuditConnector]

    when(analyticsConnectorMock.sendEvents(any())(any[HeaderCarrier], any[MdcLoggingExecutionContext])).thenReturn(Future.successful(()))

    val token = "theToken"

    val controller = new EmailVerificationController (emailConnectorMock,verificationLinkServiceMock,tokenRepoMock,verifiedEmailRepoMock,analyticsConnectorMock,auditConnector){
      override def newToken(): String = token
    }

    val templateId = "my-template"
    val recipient = "user@example.com"
    val params = Map("name" -> "Mr Joe Bloggs")
    val paramsJsonStr = Json.toJson(params).toString()
    val email = "user@email.com"
    val writeResult: Future[WriteResult] = Future.successful(DefaultWriteResult(
      ok = true,
      n = 0,
      writeErrors = Seq.empty,
      writeConcernError = None,
      code = None,
      errmsg = None
    ))

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
