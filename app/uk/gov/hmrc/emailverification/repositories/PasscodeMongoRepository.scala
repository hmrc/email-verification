/*
 * Copyright 2020 HM Revenue & Customs
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
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.emailverification.models.PasscodeDoc
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PasscodeMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent, config: AppConfig)(implicit ec: ExecutionContext)
  extends ReactiveRepository[PasscodeDoc, BSONObjectID](
    collectionName = "passcode",
    mongo          = mongoComponent.mongoConnector.db,
    domainFormat   = PasscodeDoc.format,
    idFormat       = ReactiveMongoFormats.objectIdFormats) {

  /**
   * add/update session record with passcode and email address to use.
   * Returns the new/updated passcode doc with incremented emailAttempts count.
   * Future fails with EmailPasscodeException.MaxEmailsExceeded if the emailAttempts count then exceeds maxEmailAttempts
   * @see MaxEmailsExceeded
   * @see AppConfig.maxEmailAttempts
   */
  def upsertIncrementingEmailAttempts(sessionId: SessionId, passcode: String, email: String, validityDurationMinutes: Int): Future[PasscodeDoc] = {
    val selector = Json.obj("sessionId" -> sessionId.value)
    val passcodeDoc = PasscodeDoc(sessionId.value, email, passcode, DateTime.now(UTC).plusMinutes(validityDurationMinutes), 0, 1)

    val update = Json.obj (
      "$set" -> Json.obj (
        "sessionId" -> passcodeDoc.sessionId,
        "email" -> passcodeDoc.email,
        "passcode" -> passcodeDoc.passcode.toUpperCase,
        "expireAt" -> BSONDateTime(passcodeDoc.expireAt.getMillis),
        "passcodeAttempts" -> 0
      ),
      "$inc" -> Json.obj {
        "emailAttempts" -> 1
      }
    )

    //increment email count as part of update so we only need one mongo call
    collection.findAndUpdate(
      selector                 = selector,
      update                   = update,
      fetchNewObject           = true,
      upsert                   = true,
      sort                     = None,
      fields                   = None,
      bypassDocumentValidation = false,
      collection.update(ordered = false).writeConcern,
      maxTime      = None,
      collation    = None,
      arrayFilters = Seq())
      .map { result =>
        result.value.getOrElse(throw new RuntimeException("upsert used but no Document returned")).as[PasscodeDoc]
      }
  }

  def findPasscodeBySessionId(sessionId: String): Future[Option[PasscodeDoc]] = find("sessionId" -> sessionId).map(_.headOption)

  /**
   * Returns passcode doc if one is found matching given sessionId.
   * If one is found, the passcodeAttempts field is also incremented before it's returned.
   */
  def findPasscodeAndIncrementAttempts(sessionId: SessionId): Future[Option[PasscodeDoc]] = {
    val selector = Json.obj("sessionId" -> sessionId.value)
    val update = Json.obj(
      "$inc" -> Json.obj(
        "passcodeAttempts" -> 1
      )
    )

    //increment passcode attempt count as part of the fetch so we only need one mongo call
    collection.findAndUpdate(
      selector                 = selector,
      update                   = update,
      fetchNewObject           = true,
      upsert                   = false,
      sort                     = None,
      fields                   = None,
      bypassDocumentValidation = false,
      collection.update(ordered = false).writeConcern,
      maxTime      = None,
      collation    = None,
      arrayFilters = Seq())
      .map(_.value.map(_.as[PasscodeDoc]))
  }

  override def indexes: Seq[Index] = Seq(
    Index(key    = Seq("sessionId" -> IndexType.Ascending), name = Some("sessionIdUnique"), unique = true),
    Index(key     = Seq("expireAt" -> IndexType.Ascending), name = Some("expireAtIndex"), options = BSONDocument("expireAfterSeconds" -> 0))
  )

}

