/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.emailverification.controllers

import java.util.UUID

import org.joda.time.Period
import org.joda.time.format.ISOPeriodFormat
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.mvc._
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.emailverification.connectors.EmailConnector
import uk.gov.hmrc.emailverification.repositories.VerificationTokenMongoRepository._
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.emailverification.services.VerificationLinkService
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.{NotFoundException, Upstream4xxResponse}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

case class EmailVerificationRequest(email: String, templateId: String, templateParameters: Map[String, String], linkExpiryDuration: Period, continueUrl: String)

case class TokenVerificationRequest(token: String)

object EmailVerificationRequest {
  implicit val periodReads: Reads[Period] = JsPath.read[String].map(ISOPeriodFormat.standard().parsePeriod)
  implicit val reads = Json.reads[EmailVerificationRequest]
}

object TokenVerificationRequest {
  implicit val reads = Json.reads[TokenVerificationRequest]
}

trait EmailVerificationController extends BaseController {
  def emailConnector: EmailConnector
  def verificationLinkService: VerificationLinkService
  def tokenRepo: VerificationTokenMongoRepository
  def verifiedEmailRepo: VerifiedEmailMongoRepository
  def newToken(): String

  def requestVerification() = Action.async(parse.json) { implicit httpRequest =>
    withJsonBody[EmailVerificationRequest] { request =>
      val token = newToken()
      verifiedEmailRepo.isVerified(request.email) flatMap {
        if (_) Future.successful(Conflict)
        else for {
          _ <- tokenRepo.insert(token, request.email, request.linkExpiryDuration)
          paramsWithVerificationLink = request.templateParameters + ("verificationLink" -> verificationLinkService.verificationLinkFor(token, request.continueUrl))
          _ <- emailConnector.sendEmail(request.email, request.templateId, paramsWithVerificationLink)
        } yield NoContent
      } recover {
        case ex: NotFoundException => InternalServerError(ex.toString)
        case ex: Upstream4xxResponse if ex.upstreamResponseCode == 400 => BadRequest(ex.message)
      }
    }
  }

  def validateToken() = Action.async(parse.json) { implicit httpRequest =>
    withJsonBody[TokenVerificationRequest] { request =>
      tokenRepo.findToken(request.token) flatMap {
        case Some(doc) => verifiedEmailRepo.insert(doc.email) map (_ => Created)
        case None => Future.successful(BadRequest("Token not found or expired"))
      }
    } recover {
      case e: DatabaseException if e.code.contains(DuplicateValue) => NoContent
    }
  }

}

object EmailVerificationController extends EmailVerificationController {
  override lazy val emailConnector = EmailConnector
  override lazy val verificationLinkService = VerificationLinkService
  override lazy val tokenRepo = VerificationTokenMongoRepository()
  override lazy val verifiedEmailRepo = VerifiedEmailMongoRepository()

  override def newToken() = UUID.randomUUID().toString
}
