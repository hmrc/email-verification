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

import play.api.libs.json.JsObject
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.RepositoryBaseSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.CacheItem

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import scala.concurrent.Await
import scala.concurrent.duration._

class TestEmailVerificationV2ServiceSpec extends RepositoryBaseSpec {

  trait Setup {
    val testClock: Clock = Clock.fixed(Instant.now(), ZoneId.from(ZoneOffset.UTC))
    val sendCodeTestEmail: String = "codesent@sendcode.com"
    val sendCodeV2Request: SendCodeV2Request = SendCodeV2Request(sendCodeTestEmail)
    val notSendCodeTestEmail: String = "codenotsent@sendcode.com"
    val notSendCodeV2Request: SendCodeV2Request = SendCodeV2Request(notSendCodeTestEmail)
    val testVerificationCode: String = "ABCDEF"
    val testBadVerificationCode: String = "FEDCBA"
    val testCacheItem: CacheItem = CacheItem("some-id", JsObject.empty, Instant.now(testClock), Instant.now(testClock))

    val verifyCodeV2Request: VerifyCodeV2Request = VerifyCodeV2Request(sendCodeTestEmail, testVerificationCode)
    val badVerifyCodeV2Request: VerifyCodeV2Request = VerifyCodeV2Request(notSendCodeTestEmail, testBadVerificationCode)
    val invalidEmailVerifyCodeV2Request: VerifyCodeV2Request = VerifyCodeV2Request("invalidcodesent@sendcode.com", testVerificationCode)
    val invalidCodeVerifyCodeV2Request: VerifyCodeV2Request = VerifyCodeV2Request("valid-email@email.com", testBadVerificationCode)

    val codeSentResult: SendCodeResult = SendCodeResult.codeSent()
    val codeNotSentResult: SendCodeResult = SendCodeResult.codeNotSent("boom - didn't manage to send the verification code")

    val codeVerifiedResult: VerifyCodeResult = VerifyCodeResult.codeVerified()
    val codeNotVerifiedResult: VerifyCodeResult = VerifyCodeResult.codeNotVerified("Invalid verification code")
    val codeNotFoundResult: VerifyCodeResult = VerifyCodeResult.codeNotFound("Verification code not found")

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val userAgent: UserAgent = UserAgent(Some("test-user-agent"))

    val mockEmailService: EmailService = mock[EmailService]
    val emailVerificationService = new TestEmailVerificationV2Service()
  }

  "doSendCode" should {
    "create a verificationCode, send it in an email, handle the successful response and write an audit event" in new Setup {
      val sendCodeResult: SendCodeResult = Await.result(emailVerificationService.doSendCode(sendCodeV2Request), 5.seconds)

      sendCodeResult.status shouldBe "CODE_SENT"
    }

    "create a verificationCode, send it in an email, handle the failure response and write an audit event" in
      new Setup {
        val sendCodeResult: SendCodeResult = Await.result(emailVerificationService.doSendCode(notSendCodeV2Request), 5.seconds)

        sendCodeResult.status shouldBe "CODE_NOT_SENT"
      }
  }

  "verifyCode" should {
    "verify given a valid code and email" in new Setup {
      val sendCodeResult: SendCodeResult = Await.result(emailVerificationService.doSendCode(sendCodeV2Request), 5.seconds)
      val verifyCodeResult: VerifyCodeResult = Await.result(emailVerificationService.doVerifyCode(verifyCodeV2Request), 5.seconds)

      sendCodeResult.status       shouldBe "CODE_SENT"
      verifyCodeResult.isVerified shouldBe true
    }

    "not verify given an invalid email" in new Setup {
      val sendCodeResult: SendCodeResult = Await.result(emailVerificationService.doSendCode(sendCodeV2Request), 5.seconds)
      val verifyCodeResult: VerifyCodeResult = Await.result(emailVerificationService.doVerifyCode(invalidEmailVerifyCodeV2Request), 5.seconds)

      sendCodeResult.status         shouldBe "CODE_SENT"
      verifyCodeResult.isVerified   shouldBe false
      verifyCodeResult.codeNotValid shouldBe true
    }
  }
}
