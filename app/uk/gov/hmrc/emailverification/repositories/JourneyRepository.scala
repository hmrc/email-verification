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

import com.github.ghik.silencer.silent
import com.google.inject.ImplementedBy
import org.joda.time.DateTime
import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.JSONSerializationPack.Document
import uk.gov.hmrc.emailverification.models.{Journey, Language}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[JourneyMongoRepository])
trait JourneyRepository {
  def initialise(journey: Journey): Future[Unit]
  def submitEmail(journeyId: String, email: String): Future[Option[Journey]]
  def recordPasscodeAttempt(journeyId: String): Future[Option[Journey]]
  def recordPasscodeResent(journeyId: String): Future[Option[Journey]]

  def get(journeyId: String): Future[Option[Journey]]
}

private class JourneyMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)(implicit ec: ExecutionContext)
  extends ReactiveRepository[JourneyMongoRepository.Entity, String](
    collectionName = "journey",
    mongo          = mongoComponent.mongoConnector.db,
    domainFormat   = JourneyMongoRepository.mongoFormat,
    idFormat       = implicitly) with JourneyRepository {

  override def indexes: Seq[Index] = Seq(
    Index(key     = Seq("createdAt" -> IndexType.Ascending), name = Some("ttl"), options = BSONDocument("expireAfterSeconds" -> 4.hours.toSeconds))
  )

  def initialise(journey: Journey): Future[Unit] = {
    insert(
      JourneyMongoRepository.Entity(
        journeyId                 = journey.journeyId,
        credId                    = journey.credId,
        continueUrl               = journey.continueUrl,
        origin                    = journey.origin,
        accessibilityStatementUrl = journey.accessibilityStatementUrl,
        serviceName               = journey.serviceName,
        language                  = journey.language,
        emailAddress              = journey.emailAddress,
        emailEnterUrl             = journey.emailEnterUrl,
        passcode                  = journey.passcode,
        createdAt                 = DateTime.now(),
        emailAddressAttempts      = journey.emailAddressAttempts,
        passcodesSentToEmail      = journey.passcodesSentToEmail,
        passcodeAttempts          = 0
      )
    ).map(_ => ())
  }

  @silent("deprecated") // the "other" findAndUpdate is bad and should feel bad
  override def submitEmail(journeyId: String, email: String): Future[Option[Journey]] = collection.findAndUpdate(
    Json.obj("_id" -> journeyId),
    Json.obj(
      "$set" -> Json.obj("emailAddress" -> email, "passcodesSentToEmail" -> 1),
      "$inc" -> Json.obj("emailAddressAttempts" -> 1)
    )
  ).map(entity => readAsJourney(entity.value))

  @silent("deprecated")
  override def recordPasscodeAttempt(journeyId: String): Future[Option[Journey]] = collection.findAndUpdate(
    Json.obj("_id" -> journeyId),
    Json.obj(
      "$inc" -> Json.obj("passcodeAttempts" -> 1)
    )
  ).map(entity => readAsJourney(entity.value))

  @silent("deprecated")
  override def recordPasscodeResent(journeyId: String): Future[Option[Journey]] = collection.findAndUpdate(
    Json.obj("_id" -> journeyId),
    Json.obj(
      "$inc" -> Json.obj("passcodesSentToEmail" -> 1)
    )
  ).map(entity => readAsJourney(entity.value))

  private def readAsJourney(value: Option[Document]): Option[Journey] = {
    value.map(JourneyMongoRepository.mongoFormat.reads(_).recoverTotal {
      case JsError(errors) => throw new IllegalStateException(s"journey repository entity is not a valid Entity: $errors")
    }).map(_.toJourney)
  }

  override def get(journeyId: String): Future[Option[Journey]] = findById(journeyId).map(_.map(_.toJourney))
}

private object JourneyMongoRepository {

  case class Entity(
      journeyId:                 String,
      credId:                    String,
      continueUrl:               String,
      origin:                    String,
      accessibilityStatementUrl: String,
      serviceName:               String,
      language:                  Language,
      emailAddress:              Option[String],
      emailEnterUrl:             Option[String],
      passcode:                  String,
      createdAt:                 DateTime,
      emailAddressAttempts:      Int,
      passcodesSentToEmail:      Int,
      passcodeAttempts:          Int
  ) {
    def toJourney: Journey = Journey(
      journeyId,
      credId,
      continueUrl,
      origin,
      accessibilityStatementUrl,
      serviceName,
      language,
      emailAddress,
      emailEnterUrl,
      passcode,
      emailAddressAttempts,
      passcodesSentToEmail,
      passcodeAttempts
    )
  }

  val mongoFormat: OFormat[Entity] = (
    (__ \ "_id").format[String] and
    (__ \ "credId").format[String] and
    (__ \ "continueUrl").format[String] and
    (__ \ "origin").format[String] and
    (__ \ "accessibilityStatementUrl").format[String] and
    (__ \ "serviceName").format[String] and
    (__ \ "language").format[Language] and
    (__ \ "emailAddress").formatNullable[String] and
    (__ \ "emailEnterUrl").formatNullable[String] and
    (__ \ "passcode").format[String] and
    (__ \ "createdAt").format[DateTime](ReactiveMongoFormats.dateTimeFormats) and
    (__ \ "emailAddressAttempts").format[Int] and
    (__ \ "passcodesSentToEmail").format[Int] and
    (__ \ "passcodeAttempts").format[Int]
  ) (Entity.apply, unlift(Entity.unapply))
}
