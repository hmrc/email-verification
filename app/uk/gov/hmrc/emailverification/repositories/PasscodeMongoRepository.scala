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

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTimeZone.UTC
import org.joda.time.{DateTime, Period}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.emailverification.models.{PasscodeDoc, VerificationDoc}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PasscodeMongoRepository @Inject()(mongoComponent: ReactiveMongoComponent)(implicit ec:ExecutionContext)
  extends ReactiveRepository[PasscodeDoc, BSONObjectID](
    collectionName = "passcode",
    mongo          = mongoComponent.mongoConnector.db,
    domainFormat   = PasscodeDoc.format,
    idFormat       = ReactiveMongoFormats.objectIdFormats) {

  def upsert(sessionId:String, passcode: String, email: String, validity: Period)(implicit hc: HeaderCarrier): Future[WriteResult] = {
    val selector = Json.obj("sessionId"->sessionId)
    val update = PasscodeDoc(sessionId, email, passcode, dateTimeProvider().plus(validity))

    collection.update(ordered=false).one(selector, update, upsert = true)
  }

  def findPasscode(sessionId:String, passcode: String)(implicit hc: HeaderCarrier): Future[Option[PasscodeDoc]] = find("sessionId"->sessionId, "passcode" -> passcode).map(_.headOption)

  def dateTimeProvider: Function0[DateTime] = ()=>DateTime.now(UTC)

  override def indexes: Seq[Index] = Seq(
    Index(key = Seq("sessionId" -> IndexType.Ascending), name = Some("sessionIdUnique"), unique = true),
    Index(key = Seq("expireAt" -> IndexType.Ascending), name = Some("expireAtIndex"), options = BSONDocument("expireAfterSeconds" -> 0))
  )

}
