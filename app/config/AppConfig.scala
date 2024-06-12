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

import scala.concurrent.duration.Duration

class AppConfig @Inject() (val config: Configuration) {

  lazy val platformFrontendHost: String = getString("platform.frontend.host")
  lazy val emailServicePath: String = getString("microservice.services.email.path")
  lazy val passcodeEmailTemplateParameters: Map[String, String] = config
    .getOptional[Map[String, String]]("passcodeEmailTemplateParameters")
    .getOrElse(Map.empty)

  lazy val passcodeExpiryMinutes: Int = config.get[Int]("passcodeExpiryMinutes")

  private def getString(key: String) = config.get[String](key)

  lazy val maxPasscodeAttempts: Int = config.get[Int]("maxPasscodeAttempts") // passcode guess attempts
  lazy val maxAttemptsPerEmail: Int = config.get[Int]("maxEmailAttempts") // passcodes emailed to same address
  lazy val maxDifferentEmails: Int = config.get[Int]("maxDifferentEmails")
  lazy val verificationStatusRepositoryTtl: Duration = config.get[Duration]("verificationStatusRepositoryTtl")

  lazy val verifiedEmailRepoHashKey: String = config.get[String]("verifiedEmailRepo.hashKey")
  lazy val verifiedEmailRepoReplaceIndex: Boolean = config.get[Boolean]("verifiedEmailRepo.replaceIndex")
  lazy val verifiedEmailRepoTTLDays: Int = config.get[Int]("verifiedEmailRepo.ttlDays")

  // GG-6759 remove after email migration..
  lazy val emailMigrationEnabled: Boolean = config.get[Boolean]("emailMigration.enabled")
  lazy val emailMigrationStartAfterMinutes: Int = config.get[Int]("emailMigration.startAfterMinutes")
  lazy val emailMigrationBatchSize: Int = config.get[Int]("emailMigration.batchSize")
  lazy val emailMigrationBatchDelayMillis: Int = config.get[Int]("emailMigration.batchDelayMillis")
  lazy val emailMigrationMaxDurationSeconds: Int = config.get[Int]("emailMigration.maxDurationSeconds")

  lazy val dropVerifiedEmailCollectionEnabled: Boolean = config.get[Boolean]("verifiedEmailCollection.drop.enabled")
  lazy val dropVerifiedEmailCollectionDelaySecs: Int = config.get[Int]("verifiedEmailCollection.drop.delaySeconds")

  lazy val verifiedEmailCheckCollection: WhichToUse = WhichToUse.forCollectionToCheck(config.get[String]("verifiedEmailCheckCollection"))

  lazy val verifiedEmailUpdateCollection: WhichToUse = WhichToUse.forCollectionToUpdate(config.get[String]("verifiedEmailUpdateCollection"))

}
