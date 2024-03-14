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
import org.mongodb.scala.model.IndexModel
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.emailverification.models.{Journey, Language}
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[JourneyMongoRepository])
trait JourneyRepository {
  def initialise(journey: Journey): Future[Unit]
  def submitEmail(journeyId: String, email: String): Future[Option[Journey]]
  def recordPasscodeAttempt(journeyId: String): Future[Option[Journey]]
  def recordPasscodeResent(journeyId: String): Future[Option[Journey]]

  def get(journeyId: String): Future[Option[Journey]]
  def findByCredId(credId: String): Future[Seq[Journey]]
}

@Singleton
class JourneyMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[JourneyMongoRepository.Entity](
      collectionName = "journey",
      mongoComponent = mongoComponent,
      domainFormat = JourneyMongoRepository.mongoFormat,
      indexes = Seq(
        IndexModel(Indexes.ascending("createdAt"), IndexOptions().name("ttl").expireAfter(4, TimeUnit.HOURS)),
        IndexModel(Indexes.ascending("credId"), IndexOptions().name("credId").unique(false))
      ),
      replaceIndexes = false
    )
    with JourneyRepository {

  def initialise(journey: Journey): Future[Unit] = {
    collection
      .insertOne(
        JourneyMongoRepository.Entity(
          journeyId = journey.journeyId,
          credId = journey.credId,
          continueUrl = journey.continueUrl,
          origin = journey.origin,
          accessibilityStatementUrl = journey.accessibilityStatementUrl,
          serviceName = journey.serviceName,
          language = journey.language,
          emailAddress = journey.emailAddress,
          enterEmailUrl = journey.enterEmailUrl,
          backUrl = journey.backUrl,
          pageTitle = journey.pageTitle,
          passcode = journey.passcode,
          createdAt = Instant.now(),
          emailAddressAttempts = journey.emailAddressAttempts,
          passcodesSentToEmail = journey.passcodesSentToEmail,
          passcodeAttempts = 0
        )
      )
      .toFuture()
      .map(_ => ())
  }

  override def submitEmail(journeyId: String, email: String): Future[Option[Journey]] = {
    val isEmailAddressTheSame = collection
      .find(
        filter = Filters.equal("_id", journeyId)
      )
      .headOption()
      .map(entity => entity.map(_.emailAddress.contains(email)))

    isEmailAddressTheSame.flatMap {
      case Some(true) =>
        collection
          .findOneAndUpdate(
            filter = Filters.equal("_id", journeyId),
            update = Updates.inc("passcodesSentToEmail", 1),
            options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
          )
          .toFutureOption()
          .map(_.map(_.toJourney))
      case Some(false) =>
        collection
          .findOneAndUpdate(
            filter = Filters.equal("_id", journeyId),
            update = Updates.combine(
              Updates.set("emailAddress", email),
              Updates.set("passcodesSentToEmail", 1),
              Updates.inc("emailAddressAttempts", 1)
            ),
            options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
          )
          .toFutureOption()
          .map(_.map(_.toJourney))
      case None => Future.successful(None)
    }

  }

  override def recordPasscodeAttempt(journeyId: String): Future[Option[Journey]] = collection
    .findOneAndUpdate(
      Filters.equal("_id", journeyId),
      Updates.inc("passcodeAttempts", 1)
    )
    .toFutureOption()
    .map(_.map(_.toJourney))

  override def recordPasscodeResent(journeyId: String): Future[Option[Journey]] = collection
    .findOneAndUpdate(
      Filters.equal("_id", journeyId),
      Updates.inc("passcodesSentToEmail", 1)
    )
    .toFutureOption()
    .map(_.map(_.toJourney))

  override def get(journeyId: String): Future[Option[Journey]] = collection
    .find(Filters.equal("_id", journeyId))
    .headOption()
    .map(_.map(_.toJourney))

  def findByCredId(credId: String): Future[Seq[Journey]] = collection
    .find(Filters.equal("credId", credId))
    .toFuture()
    .map(_.map(_.toJourney))
}

object JourneyMongoRepository {

  case class Entity(
    journeyId: String,
    credId: String,
    continueUrl: String,
    origin: String,
    accessibilityStatementUrl: String,
    serviceName: String,
    language: Language,
    emailAddress: Option[String],
    enterEmailUrl: Option[String],
    backUrl: Option[String],
    pageTitle: Option[String],
    passcode: String,
    createdAt: Instant,
    emailAddressAttempts: Int,
    passcodesSentToEmail: Int,
    passcodeAttempts: Int
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
      enterEmailUrl,
      backUrl,
      pageTitle,
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
      (__ \ "enterEmailUrl").formatNullable[String] and
      (__ \ "backUrl").formatNullable[String] and
      (__ \ "pageTitle").formatNullable[String] and
      (__ \ "passcode").format[String] and
      (__ \ "createdAt").format[Instant](uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.instantFormat) and
      (__ \ "emailAddressAttempts").format[Int] and
      (__ \ "passcodesSentToEmail").format[Int] and
      (__ \ "passcodeAttempts").format[Int]
  )(Entity.apply, unlift(Entity.unapply))
}
