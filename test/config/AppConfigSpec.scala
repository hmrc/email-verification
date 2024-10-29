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

package config

import play.api.Configuration
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class AppConfigSpec extends AnyWordSpec with Matchers {

  "simple string configuration values" should {

    "be available when configuration is defined" in new Setup(
      Map(
        "appName"                                              -> "the-app-name",
        "platform.frontend.host"                               -> "some-host",
        "microservice.services.email.path"                     -> "some-path",
        "microservice.services.access-control.request.formUrl" -> "access-request-form-url",
        "microservice.services.access-control.enabled"         -> "false",
        "microservice.services.access-control.allow-list"      -> List()
      )
    ) {
      appConfig.platformFrontendHost   shouldBe "some-host"
      appConfig.emailServicePath       shouldBe "some-path"
      appConfig.accessRequestFormUrl   shouldBe "access-request-form-url"
      appConfig.accessControlEnabled   shouldBe false
      appConfig.accessControlAllowList shouldBe empty
    }

    "throw exceptions when configuration not defined" in new Setup(
      Map(
        "appName" -> "the-app-name"
      )
    ) {
      val exception1: RuntimeException = intercept[RuntimeException](appConfig.platformFrontendHost)
      exception1.getMessage shouldBe s"hardcoded value: No configuration setting found for key 'platform'"

      val exception2: RuntimeException = intercept[RuntimeException](appConfig.emailServicePath)
      exception2.getMessage shouldBe s"hardcoded value: No configuration setting found for key 'microservice'"

      val exception3: RuntimeException = intercept[RuntimeException](appConfig.accessRequestFormUrl)
      exception3.getMessage shouldBe s"hardcoded value: No configuration setting found for key 'microservice'"

      val exception4: RuntimeException = intercept[RuntimeException](appConfig.accessControlEnabled)
      exception4.getMessage shouldBe s"hardcoded value: No configuration setting found for key 'microservice'"

      val exception5: RuntimeException = intercept[RuntimeException](appConfig.accessControlAllowList)
      exception5.getMessage shouldBe s"hardcoded value: No configuration setting found for key 'microservice'"
    }
  }

  private class Setup(testConfig: Map[String, Any] = Map.empty, env: String = "Test") {
    val appConfig = new AppConfig(Configuration.from(testConfig))
  }
}
