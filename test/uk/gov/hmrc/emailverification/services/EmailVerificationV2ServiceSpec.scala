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

import config.AppConfig
import org.mockito.{ArgumentMatchers => AM}
import play.api.libs.json.JsObject
import uk.gov.hmrc.emailverification.models.{English, SendCodeResult, SendCodeV2Request, UserAgent, VerifyCodeResult, VerifyCodeV2Request}
import uk.gov.hmrc.emailverification.repositories.{RepositoryBaseSpec, VerificationCodeV2MongoRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.cache.CacheItem

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EmailVerificationV2ServiceSpec extends RepositoryBaseSpec {

  trait Setup {
    val testClock: Clock = Clock.fixed(Instant.now(), ZoneId.from(ZoneOffset.UTC))
    val testEmail: String = "joe@bloggs.com"
    val testBadEmail: String = "not-valid@"
    val sendCodeV2Request: SendCodeV2Request = SendCodeV2Request(testEmail)
    val testVerificationCode: String = "ABCDEF"
    val testBadVerificationCode: String = "A1CDEFG"
    val testCacheItem: CacheItem = CacheItem("some-id", JsObject.empty, Instant.now(testClock), Instant.now(testClock))

    val verifyCodeV2Request: VerifyCodeV2Request = VerifyCodeV2Request(testEmail, testVerificationCode)
    val badVerifyCodeV2Request: VerifyCodeV2Request = VerifyCodeV2Request("invalid@email.com", testVerificationCode)
    val invalidEmailVerifyCodeV2Request: VerifyCodeV2Request = VerifyCodeV2Request("invalid-email@", testVerificationCode)
    val invalidCodeVerifyCodeV2Request: VerifyCodeV2Request = VerifyCodeV2Request("valid-email@email.com", testBadVerificationCode)

    val codeSentResult: SendCodeResult = SendCodeResult.codeSent()
    val codeNotSentResult: SendCodeResult = SendCodeResult.codeNotSent("boom - didn't manage to send the verification code")

    val codeVerifiedResult: VerifyCodeResult = VerifyCodeResult.codeVerified()
    val codeNotVerifiedResult: VerifyCodeResult = VerifyCodeResult.codeNotVerified("Invalid verification code")
    val codeNotFoundResult: VerifyCodeResult = VerifyCodeResult.codeNotFound("Verification code not found")

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val userAgent: UserAgent = UserAgent(Some("test-user-agent"))

    implicit val mockAppConfig: AppConfig = mock[AppConfig]
    when(mockAppConfig.appName).thenReturn("test-application")
    when(mockAppConfig.verificationCodeExpiryMinutes).thenReturn(1)

    val mockVerificationCodeGenerator: PasscodeGenerator = mock[PasscodeGenerator]
    when(mockVerificationCodeGenerator.generate()).thenReturn(testVerificationCode)

    val verificationRepository: VerificationCodeV2MongoRepository =
      new VerificationCodeV2MongoRepository(mongoComponent, mockAppConfig, new CurrentTimestampSupport())

    val mockAuditService: AuditV2Service = mock[AuditV2Service]

    val mockEmailService: EmailV2Service = mock[EmailV2Service]
    val emailVerificationService = new EmailVerificationV2Service(mockVerificationCodeGenerator, verificationRepository, mockEmailService, mockAuditService)
  }

  "doSendCode" should {
    "create a verificationCode, send it in an email, handle the successful response and write an audit event" in new Setup {
      when(mockAuditService.sendVerificationCode(testEmail, testVerificationCode, "test-application", codeSentResult))
        .thenReturn(Future.successful(()))
      when(mockEmailService.sendCode(AM.eq(testEmail), AM.eq(testVerificationCode), AM.eq("test-application"), AM.eq(English))(AM.any(), AM.any()))
        .thenReturn(Future.successful(codeSentResult))
      val sendCodeResult: SendCodeResult = Await.result(emailVerificationService.doSendCode(sendCodeV2Request), 5.seconds)

      sendCodeResult.status shouldBe "CODE_SENT"
    }

    "create a verificationCode, send it in an email, handle the failure response and write an audit event" in
      new Setup {
        when(mockAuditService.sendVerificationCode(testEmail, testVerificationCode, "test-application", codeNotSentResult))
          .thenReturn(Future.successful(()))
        when(mockEmailService.sendCode(AM.eq(testEmail), AM.eq(testVerificationCode), AM.eq("test-application"), AM.eq(English))(AM.any(), AM.any()))
          .thenReturn(Future.successful(codeNotSentResult))
        val sendCodeResult: SendCodeResult = Await.result(emailVerificationService.doSendCode(sendCodeV2Request), 5.seconds)

        sendCodeResult.status shouldBe "CODE_NOT_SENT"
      }
  }

  "verifyCode" should {
    "verify given a valid code and email" in new Setup {
      when(mockAuditService.sendVerificationCode(testEmail, testVerificationCode, "test-application", codeSentResult))
        .thenReturn(Future.successful(()))
      when(mockAuditService.verifyVerificationCode(testEmail, testVerificationCode, "test-application", codeVerifiedResult))
        .thenReturn(Future.successful(()))
      when(mockEmailService.sendCode(AM.eq(testEmail), AM.eq(testVerificationCode), AM.eq("test-application"), AM.eq(English))(AM.any(), AM.any()))
        .thenReturn(Future.successful(codeSentResult))
      val sendCodeResult: SendCodeResult = Await.result(emailVerificationService.doSendCode(sendCodeV2Request), 5.seconds)
      val verifyCodeResult: VerifyCodeResult = Await.result(emailVerificationService.doVerifyCode(verifyCodeV2Request), 5.seconds)

      sendCodeResult.status       shouldBe "CODE_SENT"
      verifyCodeResult.isVerified shouldBe true
    }

    "not verify given an invalid email" in new Setup {
      when(mockAuditService.sendVerificationCode(testEmail, testVerificationCode, "test-application", codeSentResult))
        .thenReturn(Future.successful(()))
      when(mockEmailService.sendCode(AM.eq(testEmail), AM.eq(testVerificationCode), AM.eq("test-application"), AM.eq(English))(AM.any(), AM.any()))
        .thenReturn(Future.successful(codeSentResult))
      val sendCodeResult: SendCodeResult = Await.result(emailVerificationService.doSendCode(sendCodeV2Request), 5.seconds)
      val verifyCodeResult: VerifyCodeResult = Await.result(emailVerificationService.doVerifyCode(invalidEmailVerifyCodeV2Request), 5.seconds)

      sendCodeResult.status         shouldBe "CODE_SENT"
      verifyCodeResult.isVerified   shouldBe false
      verifyCodeResult.codeNotValid shouldBe true
    }

    "not verify given an invalid code" in new Setup {
      when(mockAuditService.sendVerificationCode(testEmail, testVerificationCode, "test-application", codeSentResult))
        .thenReturn(Future.successful(()))
      when(mockEmailService.sendCode(AM.eq(testEmail), AM.eq(testVerificationCode), AM.eq("test-application"), AM.eq(English))(AM.any(), AM.any()))
        .thenReturn(Future.successful(codeSentResult))
      val sendCodeResult: SendCodeResult = Await.result(emailVerificationService.doSendCode(sendCodeV2Request), 5.seconds)
      val verifyCodeResult: VerifyCodeResult = Await.result(emailVerificationService.doVerifyCode(invalidCodeVerifyCodeV2Request), 5.seconds)

      sendCodeResult.status         shouldBe "CODE_SENT"
      verifyCodeResult.isVerified   shouldBe false
      verifyCodeResult.codeNotValid shouldBe true
    }
  }
}
