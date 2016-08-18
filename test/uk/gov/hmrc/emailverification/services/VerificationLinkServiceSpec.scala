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

package uk.gov.hmrc.emailverification.services

import org.joda.time.{DateTime, Period}
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.crypto.{Crypted, CryptoWithKeysFromConfig, PlainText}
import uk.gov.hmrc.emailverification.controllers.EmailVerificationRequest
import uk.gov.hmrc.play.test.UnitSpec

class VerificationLinkServiceSpec extends UnitSpec with MockitoSugar {
  "createVerificationLink" should {
    "create encrypted verification Link" in new Setup {
      val emailToVerify = "example@domain.com"
      val templateId = "my-lovely-template"
      val templateParams = Map("name" -> "Mr Joe Bloggs")
      val continueUrl = "http://continue-url.com"
      val expectedExpirationTimeStamp = ""

      val verificationRequest = EmailVerificationRequest(emailToVerify, templateId, templateParams, Period.parse("P1D"), continueUrl)

      val expectedJson =
        s"""{
            |  "nonce":"fixedNonce",
            |  "email":"example@domain.com",
            |  "expiration":"2016-08-19T12:45:11.631+0100",
            |  "continueUrl":"http://continue-url.com"
            |}""".stripMargin

      when(cryptoMock.encrypt(PlainText(expectedJson))).thenReturn(Crypted(expectedJson))

      underTest.createVerificationTokenValue(verificationRequest) shouldBe Json.parse(expectedJson).toString()
    }
  }

  trait Setup {
    val frontendUrl = "http://email-verification-frontend.url"
    val encryptedTokenData = "encryptedTokenData"
    val fixedNonce = "fixedNonce"
    val fixedTime = DateTime.parse("2016-08-18T12:45:11.631+0100")

    val cryptoMock = mock[CryptoWithKeysFromConfig]
    val underTest = new VerificationLinkService {
      override val emailVerificationFrontendUrl = frontendUrl

      override def crypto: CryptoWithKeysFromConfig = cryptoMock

      override def createNonce = fixedNonce

      override def currentTime = fixedTime
    }
  }

}
