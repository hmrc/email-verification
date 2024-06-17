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

import play.api.Logging
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import uk.gov.hmrc.emailverification.repositories.VerifiedHashedEmailMongoRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class VerifiedEmailService @Inject() (verifiedHashedEmailRepo: VerifiedHashedEmailMongoRepository)(implicit ec: ExecutionContext) extends Logging {

  def isVerified(mixedCaseEmail: String): Future[Boolean] = find(mixedCaseEmail).map(_.isDefined)

  /** Note: The hashed email collection lower cases emails before persisting.
    */
  def find(mixedCaseEmail: String): Future[Option[VerifiedEmail]] =
    verifiedHashedEmailRepo.find(mixedCaseEmail.toLowerCase)

  /** Note: The hashed email collection lower cases emails before persisting.
    */
  def insert(mixedCaseEmail: String): Future[Unit] =
    verifiedHashedEmailRepo.insert(mixedCaseEmail.toLowerCase)

}
