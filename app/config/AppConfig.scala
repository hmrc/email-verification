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

import play.api.Play.current
import play.api.{Configuration, Play}

trait AppConfig {
  protected def config: Configuration

  lazy val platformFrontendHost: String = getString("platform.frontend.host")
  lazy val emailServicePath: String = getString("microservice.services.email.path")
  lazy val whitelistedDomains: Set[String] = config
    .getString("whitelisted-domains")
    .map(_.split(",").map(_.trim).filter(_.nonEmpty).toSet)
    .getOrElse(Set.empty[String])

  private def getString(key: String) = config.getString(key).getOrElse(throw new RuntimeException(s"Could not find config key '$key'"))
}

object AppConfig extends AppConfig {
  override protected val config = Play.configuration
}
