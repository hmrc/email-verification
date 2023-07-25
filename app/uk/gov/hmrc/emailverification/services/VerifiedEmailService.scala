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

import config.{AppConfig, WhichToUse}
import play.api.Logging

import javax.inject.Inject
import uk.gov.hmrc.emailverification.models.{MigrationResultCollector, VerifiedEmail}
import uk.gov.hmrc.emailverification.repositories.{VerifiedEmailMongoRepository, VerifiedHashedEmailMongoRepository}

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}

class VerifiedEmailService @Inject() (
    verifiedEmailRepo:       VerifiedEmailMongoRepository,
    verifiedHashedEmailRepo: VerifiedHashedEmailMongoRepository,
    appConfig:               AppConfig
)(implicit ec: ExecutionContext) extends Logging {

  /**
   * The older plain text emails collection was stored mixed case so there could be multiple entries of same email but different case.
   * The newer hashed email collection lower cases emails before persisting.
   */
  def isVerified(mixedCaseEmail: String): Future[Boolean] = find(mixedCaseEmail).map(_.isDefined)

  /**
   * The older plain text emails collection was stored mixed case so there could be multiple entries of same email but different case.
   * The newer hashed email collection lower cases emails before persisting.
   */
  def find(mixedCaseEmail: String): Future[Option[VerifiedEmail]] = appConfig.verifiedEmailCheckCollection match {
    case WhichToUse.Old => verifiedEmailRepo.find(mixedCaseEmail)
    case WhichToUse.Both => for {
      oldVerifiedEmail <- verifiedEmailRepo.find(mixedCaseEmail)
      newVerifiedEmail <- verifiedHashedEmailRepo.find(mixedCaseEmail.toLowerCase)
    } yield {
      if (oldVerifiedEmail.isDefined != newVerifiedEmail.isDefined) {
        logger.warn(s"Email ${mixedCaseEmail.take(3)}***${mixedCaseEmail.takeRight(3)} only found in ${if (oldVerifiedEmail.isDefined) "old collection"}${if (newVerifiedEmail.isDefined) "new collection"} but expected in both.")
      }
      oldVerifiedEmail
    }
    case WhichToUse.New => verifiedHashedEmailRepo.find(mixedCaseEmail.toLowerCase)
    case other          => throw new IllegalStateException(s"Unhandled WhichToUse('${other.value}') instance")
  }

  /**
   * older plain text collection is stored mixed case, new hashed collection is stored lower cased.
   */
  def insert(mixedCaseEmail: String): Future[Unit] = appConfig.verifiedEmailUpdateCollection match {
    case WhichToUse.Both => verifiedEmailRepo.insert(mixedCaseEmail)
      .flatMap(_ => verifiedHashedEmailRepo.insert(mixedCaseEmail.toLowerCase))
    case WhichToUse.New => verifiedHashedEmailRepo.insert(mixedCaseEmail.toLowerCase)
    case WhichToUse.Old => throw new RuntimeException("Post migration the hashed repo should always be used, so only 'both' or 'new' supported in config")
    case other          => throw new IllegalStateException(s"Unhandled WhichToUse('${other.value}') instance")
  }

  def migrateEmailAddresses(): Future[MigrationResultCollector] = {
    val batchSize = appConfig.emailMigrationBatchSize
    val batchDelayMS = appConfig.emailMigrationBatchDelayMillis
    val startTime = Instant.now
    val maxDuration = Duration.ofSeconds(appConfig.emailMigrationMaxDurationSeconds)

      def migrateNext(fromIndex: Int, resultCollector: MigrationResultCollector): Future[MigrationResultCollector] = {
        verifiedEmailRepo.getBatch(fromIndex, batchSize).flatMap { recordsFound =>
          val totalReadCount = fromIndex + recordsFound.size
          val toIndex = totalReadCount - 1
          val ttlDaysBeforeNow = Instant.now().minus(appConfig.verifiedEmailRepoTTLDays, ChronoUnit.DAYS)
          val recordsNotExpired = recordsFound.filter(_._2.isAfter(ttlDaysBeforeNow))
          val expiredCount = recordsFound.size - recordsNotExpired.size
          for {
            batchInsertCount <- if (recordsNotExpired.nonEmpty) {
              val fromTimestamp = recordsFound.headOption.fold("n/a")(_._2.toString)
              val toTimestamp = recordsFound.takeRight(1).headOption.fold("n/a")(_._2.toString)
              val ignoringExpiredRecords = if (expiredCount > 0) s" Ignoring $expiredCount expired records." else ""
              logger.info(s"[GG-6759] Migrating records $fromIndex to $toIndex ($fromTimestamp to $toTimestamp) from verifiedEmail to verifiedHashedEmail collection.$ignoringExpiredRecords")
              verifiedHashedEmailRepo.insertBatch(recordsNotExpired)
            } else Future.successful(0)

            newTotalInsertedCount = resultCollector.insertedCount + batchInsertCount
            newTotalDuplicatesIgnored = resultCollector.duplicateCount + (recordsNotExpired.size - batchInsertCount)
            newTotalExpiredCount = resultCollector.expiredCount + expiredCount
            newResultCollector = MigrationResultCollector(
              readCount      = totalReadCount,
              insertedCount  = newTotalInsertedCount,
              duplicateCount = newTotalDuplicatesIgnored,
              expiredCount   = newTotalExpiredCount
            )
            migratedCount <- if (maxDuration.minus(Duration.between(startTime, Instant.now)).isNegative) {
              logger.warn(s"[GG-6759] Max duration of in ${maxDuration.getSeconds} seconds reached. $totalReadCount records read from verifiedEmail, $newTotalInsertedCount added to verifiedHashedEmail, $newTotalDuplicatesIgnored duplicates ignored and $newTotalExpiredCount were expired.")
              Future.successful(newResultCollector)
            } else if (recordsFound.size == batchSize) {
              logger.warn(s"[GG-6759] sleeping for $batchDelayMS ms")
              Thread.sleep(batchDelayMS)
              migrateNext(fromIndex + batchSize, newResultCollector)
            } else {
              val duration = Duration.between(startTime, Instant.now)
              logger.warn(s"[GG-6759] Finished migration in ${duration.toMinutes} minutes. $totalReadCount records read from verifiedEmail, $newTotalInsertedCount added to verifiedHashedEmail, $newTotalDuplicatesIgnored duplicates ignored and $newTotalExpiredCount were expired.")
              Future.successful(newResultCollector)
            }
          } yield migratedCount
        }
      }
    migrateNext(fromIndex = 0, MigrationResultCollector())
  }

}
