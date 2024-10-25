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
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, JsValue}
import play.api.mvc.{Headers, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.emailverification.models.{English, SendCodeResult, SendCodeV2Request, UserAgent}
import uk.gov.hmrc.emailverification.repositories.VerificationCodeV2MongoRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.test.IndexedMongoQueriesSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

class SendCodeServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with IndexedMongoQueriesSupport {

  "sendCode" should {
    "return OK indicating email has been sent" in new SetUp {
      val emailForTest = "joe@bloggs.com"
      when(mockAuditService.sendVerificationCode(meq(emailForTest), meq(expectedVerificationCode), meq("test-application"), meq(SendCodeResult.codeSent()))(any(), any()))
        .thenReturn(Future.successful(()))

      when(emailServiceMock.sendCode(meq(emailForTest), meq(expectedVerificationCode), meq("test-application"), meq(English))(meq(hc), meq(global)))
        .thenReturn(Future.successful(SendCodeResult.codeSent()))

      val sendCodeRequest: SendCodeV2Request = SendCodeV2Request(emailForTest)
      val result: SendCodeResult = Await.result(verifyService.doSendCode(sendCodeRequest), 5.seconds)

      result.status shouldBe "CODE_SENT"
      verify(emailServiceMock, atMostOnce()).sendCode(meq(emailForTest), meq(expectedVerificationCode), meq("test-application"), any())(meq(hc), meq(global))
    }

    "return error indicating incorrect email format" in new SetUp {
      val badEmailForTest = "joebloggscom"

      val sendCodeRequest: SendCodeV2Request = SendCodeV2Request(badEmailForTest)
      val result: SendCodeResult = Await.result(verifyService.doSendCode(sendCodeRequest), 5.seconds)

      result.status  shouldBe "CODE_NOT_SENT"
      result.message shouldBe Some("Invalid email")
    }

    "return error indicating incorrect email format - no email, blank " in new SetUp {
      val badEmailForTest = ""

      val sendCodeRequest: SendCodeV2Request = SendCodeV2Request(badEmailForTest)
      val result: SendCodeResult = Await.result(verifyService.doSendCode(sendCodeRequest), 5.seconds)

      result.status  shouldBe "CODE_NOT_SENT"
      result.message shouldBe Some("Invalid email")
    }

    "return error indicating incorrect email format - 2 @ " in new SetUp {
      val badEmailForTest = "j@oe@bloggs.com"

      val sendCodeRequest: SendCodeV2Request = SendCodeV2Request(badEmailForTest)
      val result: SendCodeResult = Await.result(verifyService.doSendCode(sendCodeRequest), 5.seconds)

      result.status  shouldBe "CODE_NOT_SENT"
      result.message shouldBe Some("Invalid email")
    }

    "return error indicating incorrect email format - semi colon " in new SetUp {
      val badEmailForTest = "j;oe@bloggs.com"

      val sendCodeRequest: SendCodeV2Request = SendCodeV2Request(badEmailForTest)
      val result: SendCodeResult = Await.result(verifyService.doSendCode(sendCodeRequest), 5.seconds)

      result.status  shouldBe "CODE_NOT_SENT"
      result.message shouldBe Some("Invalid email")
    }

    "return error indicating incorrect email format - local part > 64 characters " in new SetUp {
      val badEmailForTest = "12345678901234567890123456789012345678901234567890123456789012345@bloggs.com"

      val sendCodeRequest: SendCodeV2Request = SendCodeV2Request(badEmailForTest)
      val result: SendCodeResult = Await.result(verifyService.doSendCode(sendCodeRequest), 5.seconds)

      result.status  shouldBe "CODE_NOT_SENT"
      result.message shouldBe Some("Invalid email")
    }

    "return error indicating incorrect email format - underscore in domain part " in new SetUp {
      val badEmailForTest = "joe@blogg_s.com"

      val sendCodeRequest: SendCodeV2Request = SendCodeV2Request(badEmailForTest)
      val result: SendCodeResult = Await.result(verifyService.doSendCode(sendCodeRequest), 5.seconds)

      result.status  shouldBe "CODE_NOT_SENT"
      result.message shouldBe Some("Invalid email")
    }
  }

  trait SetUp {
    implicit val hc: HeaderCarrier = new HeaderCarrier()

    implicit val request: Request[JsValue] = FakeRequest(
      method = "POST",
      uri = "/some-uri",
      headers = Headers(HeaderNames.USER_AGENT -> "user-agent"),
      body = JsObject.empty
    )

    implicit val userAgent: UserAgent = UserAgent(request)

    implicit val appConfigMock: AppConfig = mock[AppConfig]
    when(appConfigMock.appName).thenReturn("test-application")
    when(appConfigMock.verificationCodeExpiryMinutes).thenReturn(1)

    val expectedVerificationCode = "ABCDEFG"
    val passcodeGeneratorMock: PasscodeGenerator = mock[PasscodeGenerator]
    when(passcodeGeneratorMock.generate()).thenReturn(expectedVerificationCode)

    val verificationRepository: VerificationCodeV2MongoRepository =
      new VerificationCodeV2MongoRepository(mongoComponent, appConfigMock, new CurrentTimestampSupport())

    val mockAuditService: AuditV2Service = mock[AuditV2Service]

    val emailServiceMock: EmailService = mock[EmailService]

    val verifyService =
      new EmailVerificationV2Service(passcodeGeneratorMock, verificationRepository, emailServiceMock, mockAuditService)
  }
}
