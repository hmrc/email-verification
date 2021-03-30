/*
 * Copyright 2021 HM Revenue & Customs
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
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import uk.gov.hmrc.crypto.CryptoWithKeysFromConfig
import uk.gov.hmrc.emailverification.models.ForwardUrl
import uk.gov.hmrc.gg.test.UnitSpec

class VerificationLinkServiceSpec extends UnitSpec with GuiceOneAppPerSuite {
  "createVerificationLink" should {
    "create encrypted verification Link" in new Setup {
      when(mockAppConfig.platformFrontendHost).thenReturn("")

      val continueUrl = ForwardUrl("http://continue-url.com")

      val base64CryptedJsonToken = "d3Uvc3JNSFd0V0FWOEEvKzhPM0M5TTBvOXZrNURNMEgxWkV5d29JSmE4UkpuQkROdEZQcHMxUG1tN3Z3eDhPU3hUOXdCbHAyd1dWR1NIWEp1SHEyZlE9PQ=="
      underTest.verificationLinkFor(token, continueUrl) shouldBe s"/email-verification/verify?token=$base64CryptedJsonToken"
    }
  }

  trait Setup {
    val token = "fixedNonce"
    val fixedTime: DateTime = DateTime.parse("2016-08-18T12:45:11.631+0100")

    val cryptoMock: CryptoWithKeysFromConfig = mock[CryptoWithKeysFromConfig]
    val config: Config = mock[Config]
    val mockAppConfig: AppConfig = mock[AppConfig]
    val configuration: Configuration = mock[Configuration]
    when(configuration.underlying).thenReturn(config)
    when(config.getString("token.encryption.key")) thenReturn "gvBoGdgzqG1AarzF1LY0zQ=="
    when(config.getStringList("token.encryption.previousKeys")).isLenient()

    val underTest = new VerificationLinkService()(mockAppConfig, configuration)
  }
}
