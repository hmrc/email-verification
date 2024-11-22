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

import javax.inject.Inject
import play.api.Configuration
import play.api.ConfigLoader._

import scala.concurrent.duration.Duration

class AppConfig @Inject() (val config: Configuration) {

  lazy val platformFrontendHost: String = config.get[String]("platform.frontend.host")
  lazy val emailServicePath: String = config.get[String]("microservice.services.email.path")
  lazy val passcodeEmailTemplateParameters: Map[String, String] = config
    .getOptional[Map[String, String]]("passcodeEmailTemplateParameters")
    .getOrElse(Map.empty)

  lazy val passcodeExpiryMinutes: Int = config.get[Int]("passcodeExpiryMinutes")
  lazy val verificationCodeExpiryMinutes: Long = config.get[Long]("verificationCodeExpiryMinutes")

  lazy val maxPasscodeAttempts: Int = config.get[Int]("maxPasscodeAttempts") // passcode guess attempts
  lazy val maxAttemptsPerEmail: Int = config.get[Int]("maxEmailAttempts") // passcodes emailed to same address
  lazy val maxDifferentEmails: Int = config.get[Int]("maxDifferentEmails")
  lazy val verificationStatusRepositoryTtl: Duration = config.get[Duration]("verificationStatusRepositoryTtl")

  lazy val verifiedEmailRepoHashKey: String = config.get[String]("verifiedEmailRepo.hashKey")
  lazy val verifiedEmailRepoReplaceIndex: Boolean = config.get[Boolean]("verifiedEmailRepo.replaceIndex")
  lazy val verifiedEmailRepoTTLDays: Int = config.get[Int]("verifiedEmailRepo.ttlDays")

  // V2
  lazy val appName: String = config.get[String]("appName")
  lazy val useCannedEmails: Boolean = config.get[Boolean]("microservice.services.use-canned-emails")

  lazy val accessRequestFormUrl: String = config.get[String]("microservice.services.access-control.request.formUrl")
  lazy val accessControlEnabled: Boolean = config.get[Boolean]("microservice.services.access-control.enabled")
  lazy val accessControlAllowList: Set[String] = config.get[Seq[String]]("microservice.services.access-control.allow-list").toSet
}
