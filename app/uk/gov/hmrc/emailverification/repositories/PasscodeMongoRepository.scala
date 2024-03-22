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

package uk.gov.hmrc.emailverification.repositories

import config.AppConfig
import javax.inject.{Inject, Singleton}
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, _}
import uk.gov.hmrc.emailverification.models.PasscodeDoc
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import java.time.{Clock, Instant}
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PasscodeMongoRepository @Inject() (mongoComponent: MongoComponent, config: AppConfig)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[PasscodeDoc](
      collectionName = "passcode",
      mongoComponent = mongoComponent,
      domainFormat = PasscodeDoc.format,
      indexes = Seq(
        IndexModel(Indexes.ascending("sessionId", "email"), IndexOptions().name("sessionIdUnique").unique(true)),
        IndexModel(Indexes.ascending("expireAt"), IndexOptions().name("expireAtIndex").expireAfter(0, TimeUnit.SECONDS))
      ),
      replaceIndexes = false
    ) {

  def getSessionEmailsCount(sessionId: SessionId): Future[Long] =
    collection
      .countDocuments(
        Filters.equal("sessionId", sessionId.value),
        CountOptions().limit(config.maxDifferentEmails).skip(0)
      )
      .head()

  /** add/update session record with passcode and email address to use. Returns the new/updated passcode doc with incremented emailAttempts count. Future fails with
    * EmailPasscodeException.MaxEmailsExceeded if the emailAttempts count then exceeds maxEmailAttempts
    * @see
    *   MaxEmailsExceeded
    * @see
    *   AppConfig.maxEmailAttempts
    */
  def upsertIncrementingEmailAttempts(sessionId: SessionId, passcode: String, email: String, validityDurationMinutes: Int, clock: Clock = Clock.systemUTC): Future[PasscodeDoc] = {
    val passcodeDoc = PasscodeDoc(sessionId.value, email, passcode, Instant.now(clock).plus(validityDurationMinutes, ChronoUnit.MINUTES), 0, 1)

    // increment email count as part of update so we only need one mongo call
    collection
      .findOneAndUpdate(
        filter = Filters.and(
          Filters.equal("sessionId", passcodeDoc.sessionId),
          Filters.equal("email", email)
        ),
        update = Updates.combine(
          Updates.set("sessionId", passcodeDoc.sessionId),
          Updates.set("email", passcodeDoc.email),
          Updates.set("passcode", passcodeDoc.passcode.toUpperCase),
          Updates.set("expireAt", passcodeDoc.expireAt),
          Updates.set("passcodeAttempts", 0),
          Updates.inc("emailAttempts", 1)
        ),
        options = FindOneAndUpdateOptions()
          .upsert(true)
          .bypassDocumentValidation(false)
          .returnDocument(ReturnDocument.AFTER)
      )
      .head()
  }

  def findPasscodesBySessionId(sessionId: String): Future[Seq[PasscodeDoc]] =
    collection.find(Filters.equal("sessionId", sessionId)).toFuture()

  def findPasscodeBySessionIdAndEmail(sessionId: String, email: String): Future[Seq[PasscodeDoc]] =
    collection
      .find(
        Filters.and(
          Filters.equal("sessionId", sessionId),
          Filters.equal("email", email)
        )
      )
      .toFuture()

  /** Returns passcode doc if one is found matching given sessionId and email. If one is found, the passcodeAttempts field is also incremented before it's returned.
    */
  def findPasscodeAndIncrementAttempts(sessionId: SessionId, email: String): Future[Option[PasscodeDoc]] = {
    // increment passcode attempt count as part of the fetch so we only need one mongo call
    collection
      .findOneAndUpdate(
        filter = Filters.and(
          Filters.equal("sessionId", sessionId.value),
          Filters.equal("email", email)
        ),
        Updates.inc("passcodeAttempts", 1),
        options = FindOneAndUpdateOptions()
          .upsert(false)
          .bypassDocumentValidation(false)
          .returnDocument(ReturnDocument.AFTER)
      )
      .toFutureOption()
  }

}
