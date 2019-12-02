/*
 * Copyright 2019 HM Revenue & Customs
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
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.WriteConcern
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VerifiedEmailMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)(implicit ec:ExecutionContext)
  extends ReactiveRepository[VerifiedEmail, BSONObjectID](
    collectionName = "verifiedEmail",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = VerifiedEmail.format,
    idFormat = ReactiveMongoFormats.objectIdFormats) {

  private val majority = WriteConcern.Default

  def isVerified(email: String)(implicit hc: HeaderCarrier): Future[Boolean] = this.find(email).map(_.isDefined)

  def find(email: String)(implicit hc: HeaderCarrier): Future[Option[VerifiedEmail]] = super.find("email" -> email).map(_.headOption)

  def insert(email: String)(implicit hc: HeaderCarrier): Future[WriteResult] = {
    val document = VerifiedEmail(email)
    collection.insert(ordered=false, majority).one(document)
  }

  override def indexes: Seq[Index] = Seq(
    Index(Seq("email" -> IndexType.Ascending), name = Some("emailUnique"), unique = true)
  )
}
