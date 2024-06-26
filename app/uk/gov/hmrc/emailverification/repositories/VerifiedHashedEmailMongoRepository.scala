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
import org.mongodb.scala.model._
import play.api.Logging
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.crypto.{OnewayCryptoFactory, PlainText, Sha512Crypto}
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VerifiedHashedEmailMongoRepository @Inject() (mongoComponent: MongoComponent, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[VerifiedHashedEmail](
      collectionName = "verifiedHashedEmail",
      mongoComponent = mongoComponent,
      domainFormat = VerifiedHashedEmail.format,
      indexes = Seq(
        IndexModel(Indexes.ascending("hashedEmail"), IndexOptions().name("emailUnique").unique(true)),
        IndexModel(Indexes.ascending("createdAt"), IndexOptions().name("ttl").expireAfter(appConfig.verifiedEmailRepoTTLDays, TimeUnit.DAYS))
      ),
      replaceIndexes = appConfig.verifiedEmailRepoReplaceIndex
    )
    with Logging {

  private val hasher: Sha512Crypto = OnewayCryptoFactory.sha(appConfig.verifiedEmailRepoHashKey)

  private def hashEmail(email: String): String = hasher.hash(PlainText(email)).value

  def isVerified(email: String): Future[Boolean] = find(email).map(_.isDefined)

  def find(email: String): Future[Option[VerifiedEmail]] =
    collection
      .find(Filters.equal("hashedEmail", hashEmail(email)))
      .headOption()
      .map(_.map(_ => VerifiedEmail(email)))

  def insert(email: String): Future[Unit] =
    collection
      .insertOne(VerifiedHashedEmail(hashEmail(email)))
      .headOption()
      .map(_ => ())
}

case class VerifiedHashedEmail(hashedEmail: String, createdAt: Instant = Instant.now())

object VerifiedHashedEmail {
  implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val format: OFormat[VerifiedHashedEmail] = Json.format[VerifiedHashedEmail]
}
