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

import akka.actor.{ActorSystem, Cancellable, Scheduler}
import config.AppConfig
import play.api.Logger
import uk.gov.hmrc.emailverification.services.VerifiedEmailService
import uk.gov.hmrc.gg.test.{LogCapturing, UnitSpec}
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class VerifiedEmailMigrationTaskSpec extends UnitSpec with LogCapturing {

  "initialising/starting the task" should {
    "obtain mongo lock and call verified email service migrateEmailAddresses()" in new Setup {
      withCaptureOfLoggingFrom(Logger(Class.forName("uk.gov.hmrc.emailverification.tasks.VerifiedEmailMigrationTask"))) { logs =>
        when(mockConfig.emailMigrationEnabled).thenReturn(true)
        when(mockMongoLockRepo.takeLock(*, *, *)).thenReturn(Future.successful(true))
        when(mockMongoLockRepo.releaseLock(*, *)).thenReturn(Future.unit)
        when(mockConfig.emailMigrationStartAfterMinutes).thenReturn(0)
        when(mockActorSystem.scheduler).thenReturn(myScheduler)
        when(mockVerifiedEmailService.migrateEmailAddresses()).thenReturn(Future.successful(1))

        new VerifiedEmailMigrationTask(mockVerifiedEmailService, mockMongoLockRepo, mockActorSystem, mockConfig)
        Thread.sleep(200) //allow logs to catch up
        val logMessages = logs.map(_.getMessage)
        logMessages should contain("[GG-6759] mongo lock acquired for verified-email-migration-task")
        logMessages should contain("[GG-6759] verified-email-migration-task scheduled to start after 0 minutes, mongo lock released.")
      }
    }

    "log when mongo lock already taken" in new Setup {
      withCaptureOfLoggingFrom(Logger(Class.forName("uk.gov.hmrc.emailverification.tasks.VerifiedEmailMigrationTask"))) { logs =>
        when(mockConfig.emailMigrationEnabled).thenReturn(true)
        when(mockConfig.emailMigrationStartAfterMinutes).thenReturn(0)
        when(mockMongoLockRepo.takeLock(*, *, *)).thenReturn(Future.successful(false))

        new VerifiedEmailMigrationTask(mockVerifiedEmailService, mockMongoLockRepo, mockActorSystem, mockConfig)
        Thread.sleep(200) //allow logs to catch up
        val logMessages = logs.map(_.getMessage)
        logMessages should contain("[GG-6759] Failed to acquire mongo lock for verified-email-migration-task")
      }
    }

    "log when error obtaining mongo lock" in new Setup {
      withCaptureOfLoggingFrom(Logger(Class.forName("uk.gov.hmrc.emailverification.tasks.VerifiedEmailMigrationTask"))) { logs =>
        when(mockConfig.emailMigrationEnabled).thenReturn(true)
        when(mockMongoLockRepo.takeLock(*, *, *)).thenReturn(Future.failed(new Exception("something went wrong")))
        when(mockMongoLockRepo.releaseLock(*, *)).thenReturn(Future.unit)
        when(mockConfig.emailMigrationStartAfterMinutes).thenReturn(0)

        new VerifiedEmailMigrationTask(mockVerifiedEmailService, mockMongoLockRepo, mockActorSystem, mockConfig)
        Thread.sleep(200) //allow logs to catch up
        val logMessages = logs.map(_.getMessage)
        logMessages should contain("[GG-6759] Failed to run verified-email-migration-task")
      }
    }

    "log when disabled" in new Setup {
      withCaptureOfLoggingFrom(Logger(Class.forName("uk.gov.hmrc.emailverification.tasks.VerifiedEmailMigrationTask"))) { logs =>
        when(mockConfig.emailMigrationEnabled).thenReturn(false)
        new VerifiedEmailMigrationTask(mockVerifiedEmailService, mockMongoLockRepo, mockActorSystem, mockConfig)
        Thread.sleep(200) //allow logs to catch up
        val logMessages = logs.map(_.getMessage)
        logMessages should contain("[GG-6759] verified-email-migration-task disabled")
      }
    }
  }

  trait Setup {
    val mockVerifiedEmailService = mock[VerifiedEmailService]
    val mockMongoLockRepo = mock[MongoLockRepository]
    val mockActorSystem = mock[ActorSystem]
    val mockScheduler = mock[Scheduler]
    val myScheduler = new Scheduler() {
      override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)(implicit executor: ExecutionContext): Cancellable = ???
      override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(implicit executor: ExecutionContext): Cancellable = {
        runnable.run()
        null // don't need a Cancellable here
      }
      override def maxFrequency: Double = ???
    }
    val mockConfig = mock[AppConfig]
  }

}
