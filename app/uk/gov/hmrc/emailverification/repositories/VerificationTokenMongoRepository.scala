/*
 * Copyright 2017 HM Revenue & Customs
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

import org.joda.time.DateTimeZone.UTC
import org.joda.time.{DateTime, Period}
import play.api.libs.json._
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{Indexes, ReactiveRepository}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

case class VerificationDoc(email: String, token: String, expireAt: DateTime)

object VerificationDoc {
  implicit val dateTimeFormats = ReactiveMongoFormats.dateTimeFormats
  implicit val format = Json.format[VerificationDoc]
}

abstract class VerificationTokenMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[VerificationDoc, BSONObjectID](collectionName = "verificationToken", mongo = mongo,
    domainFormat = VerificationDoc.format, idFormat = ReactiveMongoFormats.objectIdFormats) with Indexes {

  def upsert(token: String, email: String, validity: Period)(implicit hc: HeaderCarrier): Future[WriteResult] =
    collection.update(Json.obj("email" -> email), VerificationDoc(email, token, dateTimeProvider().plus(validity)), upsert = true)

  def findToken(token: String)(implicit hc: HeaderCarrier): Future[Option[VerificationDoc]] = find("token" -> token).map(_.headOption)

  def dateTimeProvider: () => DateTime

  override def indexes: Seq[Index] = Seq(
    Index(Seq("token" -> IndexType.Ascending), name = Some("tokenUnique"), unique = true),
    Index(key = Seq("expireAt" -> IndexType.Ascending), name = Some("expireAtIndex"), options = BSONDocument("expireAfterSeconds" -> 0))
  )
}

object VerificationTokenMongoRepository extends MongoDbConnection {
  val DuplicateValue = 11000
  private lazy val repo = new VerificationTokenMongoRepository {
    override val dateTimeProvider = () => DateTime.now(UTC)
  }
  def apply() = repo
}
