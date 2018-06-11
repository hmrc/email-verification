/*
 * Copyright 2018 HM Revenue & Customs
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

import org.joda.time.DateTime
import org.mockito.Mockito._
import play.api.libs.json.Json
import uk.gov.hmrc.crypto.{Crypted, CryptoWithKeysFromConfig, PlainText}
import uk.gov.hmrc.emailverification.MockitoSugarRush
import uk.gov.hmrc.emailverification.controllers.ForwardUrl
import uk.gov.hmrc.play.test.UnitSpec

class VerificationLinkServiceSpec extends UnitSpec with MockitoSugarRush {
  "createVerificationLink" should {
    "create encrypted verification Link" in new Setup {
      val emailToVerify = "example@domain.com"
      val templateId = "my-lovely-template"
      val templateParams = Some(Map("name" -> "Mr Joe Bloggs"))
      val continueUrl = ForwardUrl("http://continue-url.com")
      val expectedExpirationTimeStamp = ""

      val jsonToken = Json.parse(
        s"""{
            |  "token":"$token",
            |  "continueUrl": "${continueUrl.url}"
            |}""".stripMargin).toString()
      val cryptedJsonToken = Crypted(jsonToken)
      val base64CryptedJsonToken = new String(cryptedJsonToken.toBase64)

      when(cryptoMock.encrypt(PlainText(jsonToken))).thenReturn(cryptedJsonToken)

      underTest.verificationLinkFor(token, continueUrl) shouldBe s"http://tax-platform-url.url/email-verification/verify?token=$base64CryptedJsonToken"
    }
  }

  trait Setup {
    val frontendUrl = "http://tax-platform-url.url"
    val encryptedTokenData = "encryptedTokenData"
    val token = "fixedNonce"
    val fixedTime = DateTime.parse("2016-08-18T12:45:11.631+0100")

    val cryptoMock = mock[CryptoWithKeysFromConfig]
    val underTest = new VerificationLinkService {
      override val platformFrontendHost = frontendUrl
      override val crypto: CryptoWithKeysFromConfig = cryptoMock
    }
  }
}
