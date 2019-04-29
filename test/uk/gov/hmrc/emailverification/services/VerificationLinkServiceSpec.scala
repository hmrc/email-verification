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

package uk.gov.hmrc.emailverification.services

import com.typesafe.config.Config
import config.AppConfig
import org.joda.time.DateTime
import org.mockito.Mockito._
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.crypto.{Crypted, CryptoWithKeysFromConfig, PlainText}
import uk.gov.hmrc.emailverification.MockitoSugarRush
import uk.gov.hmrc.emailverification.models.ForwardUrl
import uk.gov.hmrc.play.test.UnitSpec

class VerificationLinkServiceSpec extends UnitSpec with MockitoSugarRush {
  "createVerificationLink" should {
    "create encrypted verification Link" in new Setup {
      val emailToVerify = "example@domain.com"
      val templateId = "my-lovely-template"
      val templateParams = Some(Map("name" -> "Mr Joe Bloggs"))
      val continueUrl = ForwardUrl("http://continue-url.com")

      val base64CryptedJsonToken = "d3Uvc3JNSFd0V0FWOEEvKzhPM0M5TTBvOXZrNURNMEgxWkV5d29JSmE4UkpuQkROdEZQcHMxUG1tN3Z3eDhPU3hUOXdCbHAyd1dWR1NIWEp1SHEyZlE9PQ=="
      underTest.verificationLinkFor(token, continueUrl) shouldBe s"/email-verification/verify?token=$base64CryptedJsonToken"
    }
  }

  trait Setup {
    val token = "fixedNonce"
    val fixedTime = DateTime.parse("2016-08-18T12:45:11.631+0100")

    val cryptoMock = mock[CryptoWithKeysFromConfig]
    val config :Config = mock[Config]
    val appConfig = mock[AppConfig]
    val configuration :Configuration = mock[Configuration]
    when(configuration.underlying) thenReturn config
    when(config.getString("token.encryption.key")) thenReturn "gvBoGdgzqG1AarzF1LY0zQ=="

    val underTest = new VerificationLinkService()(appConfig,configuration)
  }
}
