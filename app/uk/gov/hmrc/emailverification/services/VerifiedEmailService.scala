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

package uk.gov.hmrc.emailverification.services

import config.AppConfig
import play.api.Logging

import javax.inject.Inject
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import uk.gov.hmrc.emailverification.repositories.{VerifiedEmailMongoRepository, VerifiedHashedEmailMongoRepository}

import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}

class VerifiedEmailService @Inject() (
    verifiedEmailRepo:       VerifiedEmailMongoRepository,
    verifiedHashedEmailRepo: VerifiedHashedEmailMongoRepository,
    appConfig:               AppConfig
)(implicit ec: ExecutionContext) extends Logging {

  def isVerified(email: String): Future[Boolean] = verifiedHashedEmailRepo.isVerified(email).flatMap { inHashedEmailRepo =>
    if (inHashedEmailRepo) Future.successful(true)
    else verifiedEmailRepo.isVerified(email)
  }

  def find(email: String): Future[Option[VerifiedEmail]] = {
    verifiedHashedEmailRepo.find(email).flatMap { maybeVerifiedEmail =>
      if (maybeVerifiedEmail.isDefined) Future.successful(maybeVerifiedEmail)
      else verifiedEmailRepo.find(email)
    }
  }

  def insert(email: String): Future[Unit] = verifiedHashedEmailRepo.insert(email)
    .flatMap(_ => verifiedEmailRepo.insert(email))

  def migrateEmailAddresses(): Future[Int] = {
    val batchSize = appConfig.emailMigrationBatchSize
    val batchDelayMS = appConfig.emailMigrationBatchDelayMillis
    val startTime = Instant.now
    val maxDuration = Duration.ofSeconds(appConfig.emailMigrationMaxDurationSeconds)

      def migrateNext(fromIndex: Int): Future[Int] = {
        verifiedEmailRepo.getBatch(fromIndex, batchSize).flatMap { records =>
          val count = fromIndex + records.size
          val toIndex = count - 1
          for {
            _ <- if (records.nonEmpty) {
              logger.info(s"[GG-6759] Migrating records $fromIndex to $toIndex from verifiedEmail to verifiedHashedEmail collection")
              verifiedHashedEmailRepo.insertBatch(records)
            } else Future.unit
            migratedCount <- if (maxDuration.minus(Duration.between(startTime, Instant.now)).isNegative) {
              logger.warn(s"[GG-6759] Max duration of in ${maxDuration.getSeconds} seconds reached. Migrated $count records from verifiedEmail to verifiedHashedEmail collection.")
              Future.successful(count)
            } else if (records.size == batchSize) {
              logger.info(s"[GG-6759] sleeping for $batchDelayMS ms")
              Thread.sleep(batchDelayMS)
              migrateNext(fromIndex + batchSize)
            } else {
              val duration = Duration.between(startTime, Instant.now)
              logger.info(s"[GG-6759] Finished migrating $count records from verifiedEmail to verifiedHashedEmail collection in ${duration.toMinutes} minutes.")
              Future.successful(count)
            }
          } yield migratedCount
        }
      }
    migrateNext(fromIndex = 0)
  }

}
