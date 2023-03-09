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

import org.mongodb.scala.bson.{BsonObjectId, BsonString}

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VerifiedEmailMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[VerifiedEmail](
    collectionName = "verifiedEmail",
    mongoComponent = mongoComponent,
    domainFormat   = VerifiedEmail.format,
    indexes        = Seq(IndexModel(Indexes.ascending("email"), IndexOptions().name("emailUnique").unique(true))),
    replaceIndexes = false
  ) {

  def isVerified(email: String): Future[Boolean] = find(email).map(_.isDefined)

  def find(email: String): Future[Option[VerifiedEmail]] =
    collection.find(Filters.equal("email", email))
      .headOption()

  def insert(email: String): Future[Unit] =
    collection.insertOne(VerifiedEmail(email))
      .headOption()
      .map(_ => ())

  //GG-6795 - remove after emails migrated
  def getBatch(from: Int, batchSize: Int): Future[Seq[(VerifiedEmail, Instant)]] = {
    mongoComponent.database.getCollection("verifiedEmail").find()
      .skip(from)
      .limit(batchSize)
      .collect().toFuture().map(_.map{ doc =>
        val id = doc.get[BsonObjectId]("_id").getOrElse(throw new RuntimeException("verifiedEmail document should have '_id' field"))
        val email = doc.get[BsonString]("email").getOrElse(throw new RuntimeException("verifiedEmail document should have 'email' field"))
        (VerifiedEmail(email.getValue), Instant.ofEpochSecond(id.getValue.getTimestamp))
      })

  }
}
