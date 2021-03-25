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

package config

import javax.inject.Inject
import play.api.Configuration

class AppConfig @Inject() (val config: Configuration) {

  lazy val platformFrontendHost: String = getString("platform.frontend.host")
  lazy val emailServicePath: String = getString("microservice.services.email.path")
  lazy val passcodeEmailTemplateParameters: Map[String, String] = config
    .getOptional[Map[String, String]]("passcodeEmailTemplateParameters")
    .getOrElse(Map.empty)

  lazy val passcodeExpiryMinutes = config.get[Int]("passcodeExpiryMinutes")

  lazy val whitelistedDomains: Set[String] = config
    .getOptional[String]("whitelisted-domains")
    .map(_.split(",").map(_.trim).filter(_.nonEmpty).toSet)
    .getOrElse(Set.empty[String])

  private def getString(key: String) = config.get[String](key)

  lazy val maxPasscodeAttempts = config.get[Int]("maxPasscodeAttempts")
  lazy val maxEmailAttempts = config.get[Int]("maxEmailAttempts")
  lazy val maxDifferentEmails = config.get[Int]("maxDifferentEmails")
  lazy val dropPasscodeSessionIdIndexAtStartup = config.get[Boolean]("dropPasscodeSessionIdIndexAtStartup")
}

