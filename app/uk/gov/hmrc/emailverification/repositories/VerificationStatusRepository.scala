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

import com.google.inject.ImplementedBy
import config.AppConfig
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.emailverification.models.VerificationStatus
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[VerificationStatusMongoRepository])
trait VerificationStatusRepository {
  def initialise(credId: String, emailAddress: String): Future[Unit]

  def retrieve(credId: String): Future[Seq[VerificationStatus]]

  def verify(credId: String, emailAddress: String): Future[Unit]
  def lock(credId: String, emailAddress: String): Future[Unit]
  def isLocked(credId: String, emailAddress: String): Future[Boolean]
}

@Singleton
class VerificationStatusMongoRepository @Inject() (mongoComponent: MongoComponent, config: AppConfig)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[VerificationStatusMongoRepository.Entity](
      collectionName = "verificationStatus",
      mongoComponent = mongoComponent,
      domainFormat = VerificationStatusMongoRepository.mongoFormat,
      indexes = Seq(
        IndexModel(Indexes.ascending("credId"), IndexOptions().name("credId").unique(false)),
        IndexModel(Indexes.ascending("createdAt"), IndexOptions().name("ttl_index").expireAfter(config.verificationStatusRepositoryTtl.toSeconds, TimeUnit.SECONDS))
      ),
      replaceIndexes = false
    )
    with VerificationStatusRepository {

  def retrieve(credId: String): Future[Seq[VerificationStatus]] =
    collection
      .find(Filters.equal("credId", credId))
      .map(entity =>
        VerificationStatus(
          entity.emailAddress,
          entity.verified,
          entity.locked
        )
      )
      .toFuture()

  def initialise(credId: String, emailAddress: String): Future[Unit] =
    collection
      .insertOne(
        VerificationStatusMongoRepository.Entity(
          credId,
          emailAddress,
          verified = false,
          locked = false,
          Instant.now()
        )
      )
      .head()
      .map(_ => ())

  override def verify(credId: String, emailAddress: String): Future[Unit] =
    collection
      .findOneAndUpdate(
        Filters.and(
          Filters.equal("credId", credId),
          Filters.equal("emailAddress", emailAddress)
        ),
        Updates.set("verified", true)
      )
      .head()
      .map(_ => ())

  override def lock(credId: String, emailAddress: String): Future[Unit] =
    collection
      .findOneAndUpdate(
        Filters.and(
          Filters.equal("credId", credId),
          Filters.equal("emailAddress", emailAddress)
        ),
        Updates.set("locked", true)
      )
      .head()
      .map(_ => ())

  override def isLocked(credId: String, emailAddress: String): Future[Boolean] =
    collection
      .find(Filters.equal("credId", credId))
      .filter(_.emailAddress == emailAddress)
      .filter(_.locked)
      .headOption()
      .map(_.isDefined)
}

object VerificationStatusMongoRepository {

  case class Entity(
    credId: String,
    emailAddress: String,
    verified: Boolean,
    locked: Boolean,
    createdAt: Instant
  )

  object Entity {
    def unapply(model: Entity) = Some(
      model.credId,
      model.emailAddress,
      model.verified,
      model.locked,
      model.createdAt
    )
  }

  val mongoFormat: OFormat[Entity] = (
    (__ \ "credId").format[String] and
      (__ \ "emailAddress").format[String] and
      (__ \ "verified").format[Boolean] and
      (__ \ "locked").format[Boolean] and
      (__ \ "createdAt").format[Instant](uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.instantFormat)
  )(Entity.apply, unlift(Entity.unapply))
}
