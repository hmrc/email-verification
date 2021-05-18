/*
 * Copyright 2021 HM Revenue & Customs
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
import javax.inject.Inject
import org.joda.time.DateTime
import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json.{OFormat, __}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.emailverification.models.VerificationStatus
import uk.gov.hmrc.emailverification.repositories.VerificationStatusMongoRepository.Entity
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[VerificationStatusMongoRepository])
trait VerificationStatusRepository {

  def retrieve(credId: String): Future[List[VerificationStatus]]

  def initialise(credId: String, emailAddress: Option[String]): Future[Unit]

}

class VerificationStatusMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent, appConfig: AppConfig)(implicit ec: ExecutionContext)
  extends ReactiveRepository[VerificationStatusMongoRepository.Entity, BSONObjectID](
    collectionName = "verification-status",
    mongo          = mongoComponent.mongoConnector.db,
    domainFormat   = VerificationStatusMongoRepository.mongoFormat,
    idFormat       = ReactiveMongoFormats.objectIdFormats) with VerificationStatusRepository {

  override def indexes: Seq[Index] = Seq(
    Index(key    = Seq("credId" -> IndexType.Ascending), name = Some("credId"), unique = false),
    Index(key     = Seq("expireAt" -> IndexType.Ascending), name = Some("expireAtIndex"), options = BSONDocument("expireAfterSeconds" -> appConfig.verificationStatusRepositoryTtl.toSeconds))

  )

  def retrieve(credId: String): Future[List[VerificationStatus]] = find("credId" -> credId)
    .map(_.map(entity =>
      VerificationStatus(
        entity.emailAddress,
        entity.verified,
        entity.locked,
      )
    ))

  def initialise(credId: String, emailAddress: Option[String]): Future[Unit] = {
    insert(Entity(
      credId,
      emailAddress,
      verified = false,
      locked   = false,
      DateTime.now()
    )).map(_ => ())
  }

}

object VerificationStatusMongoRepository {

  case class Entity(
      credId:       String,
      emailAddress: Option[String],
      verified:     Boolean,
      locked:       Boolean,
      createdAt:    DateTime
  )

  val mongoFormat: OFormat[Entity] = (
    (__ \ "credId").format[String] and
    (__ \ "emailAddress").formatNullable[String] and
    (__ \ "verified").format[Boolean] and
    (__ \ "locked").format[Boolean] and
    (__ \ "createdAt").format[DateTime](ReactiveMongoFormats.dateTimeFormats)
  ) (Entity.apply, unlift(Entity.unapply))
}
