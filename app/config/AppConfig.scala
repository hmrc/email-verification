/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.Play.{configuration, current}
import uk.gov.hmrc.play.config.ServicesConfig

trait AppConfig {
  def platformFrontendHost: String
  def emailServicePath: String
  def whitelistedDomains: Set[String]
}

object AppConfig extends AppConfig with ServicesConfig {
  override val platformFrontendHost = getConfigValueFor("platform.frontend.host")
  override val emailServicePath = getConfigValueFor("microservice.services.email.path")
  override val whitelistedDomains = configuration
    .getString("whitelisted-domains")
    .map(_.split(",").map(_.trim).filter(_.nonEmpty).toSet)
    .getOrElse(Set.empty[String])

  private def getConfigValueFor(key: String) = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))
}
