/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.emailverification.tasks

import akka.actor.ActorSystem
import config.AppConfig
import play.api.Logging
import uk.gov.hmrc.emailverification.services.VerifiedEmailService
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration, MINUTES}

@Singleton
class VerifiedEmailMigrationTask @Inject() (
    verifiedEmailService: VerifiedEmailService,
    lockRepo:             MongoLockRepository,
    system:               ActorSystem,
    config:               AppConfig
)(implicit ec: ExecutionContext) extends Logging {

  val taskName = "verified-email-migration-task"

  if (config.emailMigrationEnabled) {
    val startDelayMinutes = config.emailMigrationStartAfterMinutes
    val startDelayDuration = FiniteDuration(startDelayMinutes, MINUTES)
    val lockService = LockService(lockRepo, lockId = taskName, ttl = 10.minutes)

    lockService.withLock {
      logger.info(s"[GG-6759] mongo lock acquired for $taskName")
      system.scheduler.scheduleOnce(startDelayDuration) {
        verifiedEmailService.migrateEmailAddresses()
      }
      Future.unit
    }.map {
      case Some(_) => logger.info(s"[GG-6759] $taskName scheduled to start after ${startDelayDuration.toCoarsest}, mongo lock released.")
      case None    => logger.info(s"[GG-6759] Failed to acquire mongo lock for $taskName")
    }.recover {
      case e: Exception => logger.error(s"[GG-6759] Failed to run $taskName", e)
    }

  } else {
    logger.info(s"[GG-6759] $taskName disabled")
  }
}
