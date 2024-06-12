/*
 * Copyright 2024 HM Revenue & Customs
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

import config.AppConfig
import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import uk.gov.hmrc.emailverification.repositories.VerifiedEmailMongoRepository

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

/** A one-off task to remove the redundant verifiedEmail collection see GG-7817 This class and config should be removed as part of the code cleanup: GG-7818
  */
class DropVerifiedEmailCollectionTask @Inject() (appConfig: AppConfig, verifiedEmailMongoRepository: VerifiedEmailMongoRepository)(implicit
  ec: ExecutionContext,
  actorSystem: ActorSystem
) extends Logging {
  if (appConfig.dropVerifiedEmailCollectionEnabled) {
    actorSystem.scheduler.scheduleOnce(appConfig.dropVerifiedEmailCollectionDelaySecs seconds) {
      verifiedEmailMongoRepository.drop().map(_ => logger.warn(s"[GG-7817] Successfully dropped the collection 'verifiedEmail'")).recover { case e =>
        logger.error(s"[GG-7817] Failed to drop the collection 'verifiedEmail'. Reason: ${e.getMessage}", e)
      }
    }
  } else {
    logger.info(s"[GG-7817] NOT trying to drop the 'verifiedEmail' collection as feature flag is disabled")
  }
}
