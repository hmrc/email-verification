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
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONInteger, BSONObjectID, BSONString}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.emailverification.models.EmailPasscodeException.MaxPasscodesAttemptsExceeded
import uk.gov.hmrc.emailverification.models.{EmailPasscodeException, PasscodeDoc}
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PasscodeMongoRepository @Inject()(mongoComponent: ReactiveMongoComponent, config:AppConfig)(implicit ec: ExecutionContext)
  extends ReactiveRepository[PasscodeDoc, BSONObjectID](
    collectionName = "passcode",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = PasscodeDoc.format,
    idFormat = ReactiveMongoFormats.objectIdFormats) {



  /**
   * add/update session record with passcode and email address to use.
   * Returns the new/updated passcode doc with incremented emailAttempts count.
   * Future fails with EmailPasscodeException.MaxEmailsExceeded if the emailAttempts count then exceeds maxEmailAttempts
   * @see MaxEmailsExceeded
   * @see AppConfig.maxEmailAttempts
   */
  def upsertIncrementingEmailAttempts(sessionId: SessionId, passcode: String, email: String, validityDurationMinutes: Int): Future[Unit] = {
    val selector = Json.obj("sessionId" -> sessionId.value)
    val passcodeDoc = PasscodeDoc(sessionId.value, email, passcode, DateTime.now(UTC).plusMinutes(validityDurationMinutes))

    val update = BSONDocument(
      "$set" -> BSONDocument (
        "sessionId" -> BSONString(passcodeDoc.sessionId),
        "email" -> BSONString(passcodeDoc.email),
        "passcode" -> BSONString(passcodeDoc.passcode.toUpperCase),
        "expireAt" -> BSONDateTime(passcodeDoc.expireAt.getMillis),
        "passcodeAttempts" -> BSONInteger(0)
      ),
      "$inc" -> BSONDocument(
        "emailAttempts" -> BSONInteger(1)
      )
    )

    //increment email count as part of update so we only need one mongo call
    collection.findAndUpdate(
      selector = selector,
      update = update,
      fetchNewObject = true,
      upsert = true,
      sort = None,
      fields = None,
      bypassDocumentValidation = false,
      collection.update(ordered = false).writeConcern,
      maxTime = None,
      collation = None,
      arrayFilters = Seq())
      .map { result =>
        result.value.getOrElse(throw new RuntimeException("upsert used but no Document returned")).as[PasscodeDoc]
      }
      .map { updatedPasscodeDoc =>
        if(updatedPasscodeDoc.emailAttempts > config.maxEmailAttempts) {
          throw new EmailPasscodeException.MaxEmailsExceeded
        }else {
          ()
        }
      }
  }

  def findPasscodeBySessionId(sessionId: String): Future[Option[PasscodeDoc]] = find("sessionId" -> sessionId).map(_.headOption)

  /**
   * Returns passcode doc if one is found matching given sessionId and passcode.
   * Future fails with MaxPasscodesAttemptsExceeded if called too many times on same sessionId
   * @see MaxPasscodesAttemptsExceeded
   * @see AppConfig.maxPasscodeAttempts
   */
  def verifyPasscode(sessionId: SessionId, passcode: String): Future[Option[PasscodeDoc]] = {
    val selector = Json.obj("sessionId" -> sessionId.value)
    val update = BSONDocument(
      "$inc" -> BSONDocument(
        "passcodeAttempts" -> BSONInteger(1)
      )
    )

    //increment passcode attempt count as part of the fetch so we only need one mongo call
    collection.findAndUpdate(
      selector = selector,
      update = update,
      fetchNewObject = true,
      upsert = false,
      sort = None,
      fields = None,
      bypassDocumentValidation = false,
      collection.update(ordered = false).writeConcern,
      maxTime = None,
      collation = None,
      arrayFilters = Seq())
      .map(_.value.map(_.as[PasscodeDoc]))
      .flatMap {
        case Some(passcodeDoc:PasscodeDoc) if passcodeDoc.passcodeAttempts > config.maxPasscodeAttempts => Future.failed(new MaxPasscodesAttemptsExceeded)
        case Some(passcodeDoc:PasscodeDoc) if passcodeDoc.passcode != passcode.toUpperCase => Future.successful(None)
        case Some(passcodeDoc:PasscodeDoc) => Future.successful(Some(passcodeDoc))
        case None => Future.successful(None)
      }
  }

  override def indexes: Seq[Index] = Seq(
    Index(key = Seq("sessionId" -> IndexType.Ascending), name = Some("sessionIdUnique"), unique = true),
    Index(key = Seq("expireAt" -> IndexType.Ascending), name = Some("expireAtIndex"), options = BSONDocument("expireAfterSeconds" -> 0))
  )

}

