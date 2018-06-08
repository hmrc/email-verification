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

package config

import org.scalatest.prop.{TableDrivenPropertyChecks, Tables}
import play.api.Configuration
import uk.gov.hmrc.play.test.UnitSpec

class AppConfigSpec extends UnitSpec {

  "simple string configuration values" should {

    "be available when configuration is defined" in new Setup(Map(
      "platform.frontend.host" -> "some-host",
      "microservice.services.email.path" -> "some-path"
    )) {
      appConfig.platformFrontendHost shouldBe "some-host"
      appConfig.emailServicePath shouldBe "some-path"
    }

    "throw exceptions when configuration not defined" in new Setup() {
      val exception1 = intercept[RuntimeException](appConfig.platformFrontendHost)
      exception1.getMessage shouldBe s"Could not find config key 'platform.frontend.host'"

      val exception2 = intercept[RuntimeException](appConfig.emailServicePath)
      exception2.getMessage shouldBe s"Could not find config key 'microservice.services.email.path'"
    }
  }

  "whitelistedDomains" should {

    "be empty when no configuration is defined" in new Setup {
      appConfig.whitelistedDomains shouldBe Set.empty[String]
    }

    val scenarios = Tables.Table[String, String, Set[String]](
      ("scenario", "configuration", "expectedValue"),
      ("be empty when configuration is empty", "", Set.empty[String]),
      ("contain a string if defined", "example.com", Set("example.com")),
      ("contain multiple strings when configuration is a list", "example.com,test.example.com", Set("example.com", "test.example.com")),
      ("filter out empty string values", " ,example.com, ,, ", Set("example.com")),
      ("trim extraneous whitespace", "     ,   ,  example.com  ,     test.example.com,    ", Set("example.com", "test.example.com"))
    )

    TableDrivenPropertyChecks.forAll(scenarios) { (scenario, configuration, expectedValue) =>
      scenario in new Setup(Map("whitelisted-domains" -> configuration)) {
        appConfig.whitelistedDomains shouldBe expectedValue
      }
    }
  }

  private class Setup(testConfig: Map[String, Any] = Map.empty, env: String = "Test") {
    val appConfig = new AppConfig {
      override protected lazy val config: Configuration = Configuration.from(testConfig)
    }
  }
}
