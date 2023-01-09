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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.emailverification.models.VerificationDoc
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.{Clock, Duration, Instant}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VerificationTokenMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[VerificationDoc](
    collectionName = "verificationToken",
    mongoComponent = mongoComponent,
    domainFormat   = VerificationDoc.format,
    indexes        = Seq(
      IndexModel(Indexes.ascending("token"), IndexOptions().name("tokenUnique").unique(true)),
      IndexModel(Indexes.ascending("expireAt"), IndexOptions().name("expireAtIndex").expireAfter(0, TimeUnit.SECONDS))
    ),
    replaceIndexes = false
  ) {

  def upsert(token: String, email: String, validity: Duration, clock: Clock = Clock.systemUTC): Future[Unit] =
    collection.findOneAndReplace(
      filter      = Filters.equal("email", email),
      replacement = VerificationDoc(email, token, Instant.now(clock).plus(validity)),
      options     = FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).toFuture().map(_ => ())

  def findToken(token: String): Future[Option[VerificationDoc]] =
    collection.find(Filters.equal("token", token))
      .headOption()
}

