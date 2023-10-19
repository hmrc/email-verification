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

package uk.gov.hmrc.emailverification.services

import org.mockito.captor.ArgCaptor
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
import uk.gov.hmrc.emailverification.models.PasscodeDoc
import uk.gov.hmrc.gg.test.UnitSpec
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.it.SessionCookieEncryptionSupport

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditServiceSpec extends UnitSpec with GuiceOneAppPerSuite with SessionCookieEncryptionSupport {

  def normalisedPasscodeVerificationRequestEventDetails(detailsMap: Map[String, String]) = {
    Map(
      "emailAddress" -> "-",
      "passcode" -> "-",
      "passcodeAttempts" -> "-",
      "sameEmailAttempts" -> "-",
      "differentEmailAttempts" -> "-",
      "success" -> "-",
      "responseCode" -> "-",
      "outcome" -> "-",
      "callingService" -> "-",
      "bearerToken" -> TestData.authBearerToken
    ) ++ detailsMap
  }

  def confirmDataEventSourceAndTags(dataEvent: DataEvent, expectedTransactionName: String) = {
    dataEvent.auditSource shouldBe "email-verification"
    dataEvent.tags shouldBe Map(
      "clientIP" -> TestData.clientIp,
      "path" -> TestData.request.path,
      HeaderNames.xSessionId -> TestData.sessionId,
      HeaderNames.akamaiReputation -> TestData.clientReputation,
      HeaderNames.xRequestId -> TestData.requestId,
      HeaderNames.deviceID -> TestData.deviceId,
      "clientPort" -> TestData.clientPort,
      "transactionName" -> expectedTransactionName
    )
  }

  def confirmSendEmailWithPasscodeEventFired(details: Map[String, String])(implicit mockAuditConnector: AuditConnector) = {
    val dataEventCaptor = ArgCaptor[DataEvent]
    verify(mockAuditConnector).sendEvent(dataEventCaptor.capture)(any, any)
    val dataEvent: DataEvent = dataEventCaptor.value

    dataEvent.auditType shouldBe "SendEmailWithPasscode"
    confirmDataEventSourceAndTags(dataEvent, expectedTransactionName = "HMRC Gateway - Email Verification - Send out passcode via Email")
    dataEvent.detail shouldBe details
  }

  def confirmCheckEmailVerifiedEvent(details: Map[String, String])(implicit mockAuditConnector: AuditConnector) = {
    val dataEventCaptor = ArgCaptor[DataEvent]
    verify(mockAuditConnector).sendEvent(dataEventCaptor.capture)(any, any)
    val dataEvent: DataEvent = dataEventCaptor.value

    dataEvent.auditType shouldBe "CheckEmailVerified"
    confirmDataEventSourceAndTags(dataEvent, expectedTransactionName = "HMRC Gateway - Email Verification - Check Email is verified")
    dataEvent.detail shouldBe details
  }

  def confirmPasscodeVerificationRequestEvent(details: Map[String, String], expectedTransactionName: String)(implicit mockAuditConnector: AuditConnector) = {
    val dataEventCaptor = ArgCaptor[DataEvent]
    verify(mockAuditConnector).sendEvent(dataEventCaptor.capture)(any, any)
    val dataEvent: DataEvent = dataEventCaptor.value

    dataEvent.auditType shouldBe "PasscodeVerificationRequest"
    confirmDataEventSourceAndTags(dataEvent, expectedTransactionName)
    dataEvent.detail shouldBe normalisedPasscodeVerificationRequestEventDetails(details)
  }

  def confirmPasscodeVerificationResponseEvent(details: Map[String, String], expectedTransactionName: String)(implicit mockAuditConnector: AuditConnector) = {
    val dataEventCaptor = ArgCaptor[DataEvent]
    verify(mockAuditConnector).sendEvent(dataEventCaptor.capture)(any, any)
    val dataEvent: DataEvent = dataEventCaptor.value

    dataEvent.auditType shouldBe "PasscodeVerificationResponse"
    confirmDataEventSourceAndTags(dataEvent, expectedTransactionName)
    dataEvent.detail shouldBe normalisedPasscodeVerificationRequestEventDetails(details)
  }

  "sendPasscodeViaEmailEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendPasscodeViaEmailEvent(emailAddress, passcode, serviceName, OK)(request)
      confirmSendEmailWithPasscodeEventFired(Map(
        "emailAddress" -> emailAddress,
        "passcode" -> passcode,
        "serviceName" -> serviceName,
        "responseCode" -> OK.toString,
        "bearerToken" -> authBearerToken
      ))
    }
  }

  "sendCheckEmailVerifiedEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendCheckEmailVerifiedEvent(emailAddress, Some("failure reason"), BAD_REQUEST)(request)
      confirmCheckEmailVerifiedEvent(Map(
        "emailAddress" -> emailAddress,
        "emailVerified" -> "false",
        "responseCode" -> BAD_REQUEST.toString,
        "bearerToken" -> authBearerToken,
        "verifyFailureReason" -> "failure reason"
      ))
    }
  }

  "sendEmailPasscodeRequestSuccessfulEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendEmailPasscodeRequestSuccessfulEvent(emailAddress, passcode, serviceName, differentEmailAttempts, passcodeDoc, CREATED)(request)
      confirmPasscodeVerificationRequestEvent(Map(
        "emailAddress" -> emailAddress,
        "passcode" -> passcode,
        "passcodeAttempts" -> passcodeAttempts.toString,
        "sameEmailAttempts" -> emailAttempts.toString,
        "differentEmailAttempts" -> differentEmailAttempts.toString,
        "success" -> "true",
        "responseCode" -> CREATED.toString,
        "outcome" -> "Successfully sent a passcode to the email address requiring verification",
        "callingService" -> serviceName
      ), "HMRC Gateway - Email Verification - Send new passcode to email address")
    }
  }

  "sendEmailRequestMissingSessionIdEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendEmailRequestMissingSessionIdEvent(emailAddress, UNAUTHORIZED)(request)
      confirmPasscodeVerificationRequestEvent(Map(
        "emailAddress" -> emailAddress,
        "success" -> "false",
        "outcome" -> "SessionId missing",
        "responseCode" -> UNAUTHORIZED.toString
      ), "HMRC Gateway - Email Verification - Send session id missing from email passcode request")
    }
  }

  "sendEmailAddressAlreadyVerifiedEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendEmailAddressAlreadyVerifiedEvent(emailAddress, serviceName, CONFLICT)(request)
      confirmPasscodeVerificationRequestEvent(Map(
        "emailAddress" -> emailAddress,
        "success" -> "true",
        "responseCode" -> CONFLICT.toString,
        "outcome" -> "Email address already verified",
        "callingService" -> serviceName,
      ), "HMRC Gateway - Email Verification - Send email address already verified request")
    }
  }

  "sendMaxEmailsExceededEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendMaxEmailsExceededEvent(emailAddress, serviceName, differentEmailAttempts, passcodeDoc, FORBIDDEN)(request)
      confirmPasscodeVerificationRequestEvent(Map(
        "emailAddress" -> emailAddress,
        "passcodeAttempts" -> passcodeAttempts.toString,
        "sameEmailAttempts" -> emailAttempts.toString,
        "differentEmailAttempts" -> differentEmailAttempts.toString,
        "success" -> "false",
        "responseCode" -> FORBIDDEN.toString,
        "outcome" -> "Max permitted passcode emails per session has been exceeded",
        "callingService" -> serviceName
      ), "HMRC Gateway - Email Verification - Send max permitted passcode emails per session has been exceeded request")
    }
  }

  "sendMaxDifferentEmailsExceededEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendMaxDifferentEmailsExceededEvent(emailAddress, serviceName, differentEmailAttempts, FORBIDDEN)(request)
      confirmPasscodeVerificationRequestEvent(Map(
        "emailAddress" -> emailAddress,
        "differentEmailAttempts" -> differentEmailAttempts.toString,
        "success" -> "false",
        "responseCode" -> FORBIDDEN.toString,
        "outcome" -> "Max permitted passcode emails per session has been exceeded",
        "callingService" -> serviceName
      ), "HMRC Gateway - Email Verification - Send max permitted number of different email addresses per session has been exceeded")
    }
  }

  "sendPasscodeEmailDeliveryErrorEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendPasscodeEmailDeliveryErrorEvent(emailAddress, serviceName, differentEmailAttempts, passcodeDoc, BAD_GATEWAY)(request)
      confirmPasscodeVerificationRequestEvent(Map(
        "emailAddress" -> emailAddress,
        "passcodeAttempts" -> passcodeAttempts.toString,
        "sameEmailAttempts" -> emailAttempts.toString,
        "differentEmailAttempts" -> differentEmailAttempts.toString,
        "success" -> "false",
        "responseCode" -> BAD_GATEWAY.toString,
        "outcome" -> "sendEmail request failed",
        "callingService" -> serviceName
      ), "HMRC Gateway - Email Verification - Send passcode to email address request failed")
    }
  }

  "sendEmailAddressConfirmedEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendEmailAddressConfirmedEvent(emailAddress, passcode, passcodeDoc, OK)(request)
      confirmPasscodeVerificationResponseEvent(Map(
        "emailAddress" -> emailAddress,
        "passcode" -> passcode,
        "passcodeAttempts" -> passcodeAttempts.toString,
        "sameEmailAttempts" -> emailAttempts.toString,
        "success" -> "true",
        "responseCode" -> OK.toString,
        "outcome" -> "Email address confirmed",
      ), "HMRC Gateway - Email Verification - Send email address confirmed request")
    }
  }

  "sendEmailAddressNotFoundOrExpiredEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendEmailAddressNotFoundOrExpiredEvent(emailAddress, passcode, NOT_FOUND)(request)
      confirmPasscodeVerificationResponseEvent(Map(
        "emailAddress" -> emailAddress,
        "passcode" -> passcode,
        "success" -> "false",
        "responseCode" -> NOT_FOUND.toString,
        "outcome" -> "Email address not found or verification attempt time expired"
      ), "HMRC Gateway - Email Verification - Send email address not found or verification attempt time expired request")
    }
  }

  "sendPasscodeMatchNotFoundOrExpiredEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendPasscodeMatchNotFoundOrExpiredEvent(emailAddress, passcode, passcodeDoc, NOT_FOUND)(request)
      confirmPasscodeVerificationResponseEvent(Map(
        "emailAddress" -> emailAddress,
        "passcode" -> passcode,
        "passcodeAttempts" -> passcodeAttempts.toString,
        "sameEmailAttempts" -> emailAttempts.toString,
        "success" -> "false",
        "responseCode" -> NOT_FOUND.toString,
        "outcome" -> "Email verification passcode match not found or time expired",
      ), "HMRC Gateway - Email Verification - Send email verification passcode match not found or time expired request")
    }
  }

  "sendVerificationRequestMissingSessionIdEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendVerificationRequestMissingSessionIdEvent(emailAddress, passcode, UNAUTHORIZED)(request)
      confirmPasscodeVerificationResponseEvent(Map(
        "emailAddress" -> emailAddress,
        "passcode" -> passcode,
        "success" -> "false",
        "responseCode" -> UNAUTHORIZED.toString,
        "outcome" -> "SessionId missing",
      ), "HMRC Gateway - Email Verification - Send session id missing from passcode verification request")
    }
  }

  "sendMaxPasscodeAttemptsExceededEvent" should {
    "fire audit event with appropriate fields" in new Setup {
      auditService.sendMaxPasscodeAttemptsExceededEvent(emailAddress, passcode, passcodeDoc, FORBIDDEN)(request)
      confirmPasscodeVerificationResponseEvent(Map(
        "emailAddress" -> emailAddress,
        "passcode" -> passcode,
        "passcodeAttempts" -> passcodeAttempts.toString,
        "sameEmailAttempts" -> emailAttempts.toString,
        "success" -> "false",
        "responseCode" -> FORBIDDEN.toString,
        "outcome" -> "Max permitted passcode verification attempts per session has been exceeded",
      ), "HMRC Gateway - Email Verification - Send max permitted passcode verification attempts per session has been exceeded request")
    }
  }

  object TestData extends TestData
  trait TestData {
    val emailAddress = "someone@somewhere.com"
    val passcode = "DFGHJK"
    val serviceName = "Service Name"
    val authBearerToken = "auth_bearer_token"
    val passcodeAttempts = 1
    val emailAttempts = 1
    val differentEmailAttempts = 1L
    val passcodeExpireAt = Instant.now

    val clientIp = "192.168.0.1"
    val clientPort = "443"
    val clientReputation = "totally reputable"
    val requestId = "requestId"
    val deviceId = "deviceId"
    val sessionId = "sessionId"

    val passcodeDoc = PasscodeDoc(
      sessionId        = sessionId,
      email            = emailAddress,
      passcode         = passcode,
      expireAt         = passcodeExpireAt,
      passcodeAttempts = passcodeAttempts,
      emailAttempts    = emailAttempts
    )

    val request = FakeRequest()
      .withHeaders(HeaderNames.trueClientIp -> clientIp)
      .withHeaders(HeaderNames.trueClientPort -> clientPort)
      .withHeaders(HeaderNames.akamaiReputation -> clientReputation)
      .withHeaders(HeaderNames.xRequestId -> requestId)
      .withHeaders(HeaderNames.deviceID -> deviceId)
      .withHeaders(HeaderNames.xSessionId -> sessionId)
      .withHeaders(HeaderNames.authorisation -> authBearerToken)
  }

  trait Setup extends TestData {
    implicit val mockAuditConnector: AuditConnector = mock[AuditConnector]
    when(mockAuditConnector.sendEvent(any)(any, any)).thenReturn(Future.successful(AuditResult.Success))
    val auditService = new AuditService(mockAuditConnector)
  }

}
