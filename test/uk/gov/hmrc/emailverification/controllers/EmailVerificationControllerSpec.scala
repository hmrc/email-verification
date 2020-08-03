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
import org.mockito.ArgumentMatchers.{eq => EQ}
import org.mockito.Mockito._
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.stubControllerComponents
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import uk.gov.hmrc.emailverification.connectors.{EmailConnector, PlatformAnalyticsConnector}
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.emailverification.services.VerificationLinkService
import uk.gov.hmrc.gg.test.UnitSpec
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EmailVerificationControllerSpec extends UnitSpec {

  "requestVerification" should {
    "send email containing verificationLink param and return success response" in new Setup {
      val verificationLink = "verificationLink"
      when(verifiedEmailRepoMock.isVerified(EQ(recipient))(any[HeaderCarrier])).thenReturn(Future.successful(false))
      when(verificationLinkServiceMock.verificationLinkFor(token, ForwardUrl("http://some/url"))).thenReturn(verificationLink)
      when(emailConnectorMock.sendEmail(any[String], any[String], any[Map[String,String]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(202, ""))  )
      when(tokenRepoMock.upsert(any[String], any[String], any[Period])(any[HeaderCarrier])).thenReturn(writeResult)
      when(appConfigMock.whitelistedDomains) thenReturn Set.empty[String]

      val result: Result = await(controller.requestVerification()(request.withBody(validRequest)))

      status(result) shouldBe Status.CREATED
      verify(tokenRepoMock).upsert(EQ(token), EQ(recipient), EQ(Period.days(2)))(any[HeaderCarrier])
      verify(emailConnectorMock).sendEmail(EQ(recipient), EQ(templateId), EQ(params + ("verificationLink" -> verificationLink)))(any[HeaderCarrier],any[ExecutionContext])
      val captor: ArgumentCaptor[GaEvent] = ArgumentCaptor.forClass(classOf[GaEvent])
      verify(analyticsConnectorMock).sendEvents(captor.capture())(any[HeaderCarrier],any[ExecutionContext])
      captor.getValue shouldBe GaEvents.verificationRequested
    }

    "return 400 if upstream email service returns bad request and should not create mongo entry" in new Setup {
      val verificationLink = "verificationLink"
      when(verifiedEmailRepoMock.isVerified(EQ(recipient))(any[HeaderCarrier])).thenReturn(Future.successful(false))
      when(verificationLinkServiceMock.verificationLinkFor(token, ForwardUrl("http://some/url"))).thenReturn(verificationLink)
      when(emailConnectorMock.sendEmail(any[String], any[String], any[Map[String,String]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(UpstreamErrorResponse.apply("Bad Request from email", 400)))
      when(appConfigMock.whitelistedDomains) thenReturn Set.empty[String]

      val result: Result = await(controller.requestVerification()(request.withBody(validRequest)))

      status(result) shouldBe Status.BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"
      verify(emailConnectorMock).sendEmail(EQ(recipient), EQ(templateId), EQ(params + ("verificationLink" -> verificationLink)))(any[HeaderCarrier],any[ExecutionContext])
      verify(auditConnector).sendExtendedEvent(any)(any, any)
      verify(analyticsConnectorMock).sendEvents(any)(any, any)
      verifyNoInteractions(tokenRepoMock)
    }

    "return 409 when email already registered" in new Setup {
      when(verifiedEmailRepoMock.isVerified(EQ(recipient))(any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(appConfigMock.whitelistedDomains) thenReturn Set.empty[String]

      val result: Result = await(controller.requestVerification()(request.withBody(validRequest)))
      status(result) shouldBe Status.CONFLICT
    }
  }

  "validateToken" should {
    "return 201 when the token is valid" in new Setup {
      when(tokenRepoMock.findToken(EQ(someToken))(any[HeaderCarrier])).thenReturn(Future.successful(Some(VerificationDoc(email, someToken, DateTime.now()))))
      when(verifiedEmailRepoMock.insert(EQ(email))(any[HeaderCarrier])).thenReturn(writeResult)
      when(verifiedEmailRepoMock.find(EQ(email))(any[HeaderCarrier])).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.validateToken()(request.withBody(Json.obj("token" -> someToken)))

      status(result) shouldBe Status.CREATED
      verify(verifiedEmailRepoMock).insert(EQ(email))(any[HeaderCarrier])
      verify(verifiedEmailRepoMock).find(EQ(email))(any[HeaderCarrier])
      val captor: ArgumentCaptor[GaEvent] = ArgumentCaptor.forClass(classOf[GaEvent])
      verify(analyticsConnectorMock).sendEvents(captor.capture())(any[HeaderCarrier],any[ExecutionContext])
      captor.getValue shouldBe GaEvents.verificationSuccess
    }

    "return 204 when the token is valid and the email was already validated" in new Setup {
      when(tokenRepoMock.findToken(EQ(someToken))(any[HeaderCarrier])).thenReturn(Future.successful(Some(VerificationDoc(email, someToken, DateTime.now()))))
      when(verifiedEmailRepoMock.find(EQ(email))(any[HeaderCarrier])).thenReturn(Future.successful(Some(VerifiedEmail(email))))

      val result: Future[Result] = controller.validateToken()(request.withBody(Json.obj("token" -> someToken)))

      status(result) shouldBe Status.NO_CONTENT
      verify(verifiedEmailRepoMock).find(EQ(email))(any[HeaderCarrier])
      verifyNoMoreInteractions(verifiedEmailRepoMock)
      val captor: ArgumentCaptor[GaEvent] = ArgumentCaptor.forClass(classOf[GaEvent])
      verify(analyticsConnectorMock).sendEvents(captor.capture())(any[HeaderCarrier],any[ExecutionContext])
      captor.getValue shouldBe GaEvents.verificationSuccess
    }

    "return 400 when the token does not exist in mongo" in new Setup {
      when(tokenRepoMock.findToken(EQ(someToken))(any[HeaderCarrier])).thenReturn(Future.successful(None))

      val result: Result = await(controller.validateToken()(request.withBody(Json.obj("token" -> someToken))))

      status(result) shouldBe Status.BAD_REQUEST
      val captor: ArgumentCaptor[GaEvent] = ArgumentCaptor.forClass(classOf[GaEvent])
      verify(analyticsConnectorMock).sendEvents(captor.capture())(any[HeaderCarrier],any[ExecutionContext])
      captor.getValue shouldBe GaEvents.verificationFailed
      verifyNoInteractions(verifiedEmailRepoMock)
    }
  }

  "verifiedEmail" should {
    "return 200 with verified email in the body" in new Setup {
      when(verifiedEmailRepoMock.find(EQ(email))(any[HeaderCarrier])).thenReturn(Future.successful(Some(VerifiedEmail(email))))
      val response: Future[Result] = controller.verifiedEmail()(request.withBody(Json.obj("email" -> email)))
      status(response) shouldBe 200
      contentAsJson(response).as[VerifiedEmail] shouldBe VerifiedEmail(email)
      verify(verifiedEmailRepoMock).find(EQ(email))(any[HeaderCarrier])
      verifyNoMoreInteractions(verifiedEmailRepoMock)
    }

    "return 404 if email not found" in new Setup {
      when(verifiedEmailRepoMock.find(EQ(email))(any[HeaderCarrier])).thenReturn(Future.successful(None))
      val response: Future[Result] = controller.verifiedEmail()(request.withBody(Json.obj("email" -> email)))
      status(response) shouldBe 404
      verify(verifiedEmailRepoMock).find(EQ(email))(any[HeaderCarrier])
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
    implicit val appConfigMock : AppConfig = mock[AppConfig]
    val auditConnector:AuditConnector = mock[AuditConnector]

    val token = "theToken"

    val controller: EmailVerificationController = new EmailVerificationController (emailConnectorMock,verificationLinkServiceMock,tokenRepoMock,verifiedEmailRepoMock,analyticsConnectorMock,auditConnector, stubControllerComponents()){
      override def newToken(): String = token
    }

    val templateId: String = "my-template"
    val recipient: String = "user@example.com"
    val params: Map[String, String] = Map("name" -> "Mr Joe Bloggs")
    val paramsJsonStr: String = Json.toJson(params).toString()
    val email: String = "user@email.com"
    val writeResult: Future[WriteResult] = Future.successful(UpdateWriteResult(
      ok = true,
      n = 0,
      writeErrors = Seq.empty,
      writeConcernError = None,
      code = None,
      errmsg = None,
      nModified = 0,
      upserted = Seq()
    ))

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
