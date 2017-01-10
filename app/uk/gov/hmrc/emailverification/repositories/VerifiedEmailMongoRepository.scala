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

import play.api.libs.json.Json
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{Indexes, ReactiveRepository}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

case class VerifiedEmail(email: String)

object VerifiedEmail {
  implicit val format = Json.format[VerifiedEmail]
}

abstract class VerifiedEmailMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[VerifiedEmail, BSONObjectID](collectionName = "verifiedEmail", mongo = mongo,
    domainFormat = VerifiedEmail.format, idFormat = ReactiveMongoFormats.objectIdFormats) with Indexes {

  def isVerified(email: String)(implicit hc: HeaderCarrier) = this.find(email).map(_.isDefined)

  def find(email: String)(implicit hc: HeaderCarrier) = super.find("email" -> email).map(_.headOption)

  def insert(email: String)(implicit hc: HeaderCarrier): Future[WriteResult] = insert(VerifiedEmail(email))

  override def indexes: Seq[Index] = Seq(
    Index(Seq("email" -> IndexType.Ascending), name = Some("emailUnique"), unique = true)
  )
}


object VerifiedEmailMongoRepository extends MongoDbConnection {
  private lazy val repo = new VerifiedEmailMongoRepository {}

  def apply() = repo
}
