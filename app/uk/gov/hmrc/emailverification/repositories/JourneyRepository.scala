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
import play.api.libs.json.{Format, Json, OFormat, __}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.emailverification.models.{Email, Journey}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[JourneyMongoRepository])
trait JourneyRepository {
  def initialise(journey: Journey): Future[Unit]
}

private class JourneyMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent, config: AppConfig)(implicit ec: ExecutionContext)
  extends ReactiveRepository[JourneyMongoRepository.Entity, BSONObjectID](
    collectionName = "journey",
    mongo          = mongoComponent.mongoConnector.db,
    domainFormat   = JourneyMongoRepository.format,
    idFormat       = ReactiveMongoFormats.objectIdFormats) with JourneyRepository {

  override def indexes: Seq[Index] = Seq(
    Index(key    = Seq("journeyId" -> IndexType.Ascending), name = Some("journeyIdUnique"), unique = true),
    Index(key     = Seq("expireAt" -> IndexType.Ascending), name = Some("expireAtIndex"), options = BSONDocument("expireAfterSeconds" -> 4.hours.toSeconds))
  )

  def initialise(journey: Journey): Future[Unit] = {
    insert(
      JourneyMongoRepository.Entity(
        journeyId                 = journey.journeyId,
        credId                    = journey.credId,
        continueUrl               = journey.continueUrl,
        origin                    = journey.origin,
        accessibilityStatementUrl = journey.accessibilityStatementUrl,
        email                     = journey.email,
        passcode                  = journey.passcode,
        createdAt                 = DateTime.now(),
        passcodeAttempts          = 0,
        emailAttempts             = 0
      )
    ).map(_ => ())
  }
}

private object JourneyMongoRepository {

  case class Entity(
      journeyId:                 String,
      credId:                    String,
      continueUrl:               String,
      origin:                    String,
      accessibilityStatementUrl: String,
      email:                     Option[Email],
      passcode:                  String,
      createdAt:                 DateTime,
      passcodeAttempts:          Int,
      emailAttempts:             Int
  )

  private implicit val dateTimeFormats: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  val format = Json.format[Entity]

  val mongoFormat: OFormat[Entity] = (
    (__ \ "_id").format[String] and
    (__ \ "credId").format[String] and
    (__ \ "continueUrl").format[String] and
    (__ \ "origin").format[String] and
    (__ \ "accessibilityStatementUrl").format[String] and
    (__ \ "email").formatNullable[Email] and
    (__ \ "passcode").format[String] and
    (__ \ "createdAt").format[DateTime](ReactiveMongoFormats.dateTimeFormats) and
    (__ \ "passcodeAttempts").format[Int] and
    (__ \ "emailAttempts").format[Int]
  ) (Entity.apply, unlift(Entity.unapply))
}
