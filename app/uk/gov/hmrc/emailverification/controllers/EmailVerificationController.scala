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
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.mvc._
import uk.gov.hmrc.emailverification.connectors.EmailConnector
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.emailverification.services.VerificationLinkService
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier}

import scala.concurrent.Future

case class EmailVerificationRequest(email: String, templateId: String, templateParameters: Option[Map[String, String]], linkExpiryDuration: Period, continueUrl: String)

case class TokenVerificationRequest(token: String)

object EmailVerificationRequest {
  implicit val periodReads: Reads[Period] = JsPath.read[String].map(ISOPeriodFormat.standard().parsePeriod)
  implicit val reads = Json.reads[EmailVerificationRequest]
}

object TokenVerificationRequest {
  implicit val reads = Json.reads[TokenVerificationRequest]
}

trait EmailVerificationController extends BaseControllerWithJsonErrorHandling {
  def emailConnector: EmailConnector

  def verificationLinkService: VerificationLinkService

  def tokenRepo: VerificationTokenMongoRepository

  def verifiedEmailRepo: VerifiedEmailMongoRepository

  def newToken(): String

  private def sendEmailAndCreateVerification(request: EmailVerificationRequest)(implicit hc: HeaderCarrier) = {
    val token = newToken()
    val paramsWithVerificationLink = request.templateParameters.getOrElse(Map.empty) + ("verificationLink" -> verificationLinkService.verificationLinkFor(token, request.continueUrl))
    for {
      _ <- emailConnector.sendEmail(request.email, request.templateId, paramsWithVerificationLink)
      _ <- tokenRepo.upsert(token, request.email, request.linkExpiryDuration)
    } yield Created
  }

  def requestVerification() = Action.async(parse.json) {
    implicit httpRequest =>
      withJsonBody[EmailVerificationRequest] { request =>
        verifiedEmailRepo.isVerified(request.email) flatMap {
          case true => Future.successful(Conflict(Json.toJson(ErrorResponse("EMAIL_VERIFIED_ALREADY", "Email has already been verified"))))
          case false => sendEmailAndCreateVerification(request)
        } recover {
          case ex: BadRequestException => BadRequest(Json.toJson(ErrorResponse("BAD_EMAIL_REQUEST", ex.getMessage)))
        }
      }
  }

  def validateToken() = Action.async(parse.json) {
    def createEmailIfNotExist(email: String)(implicit hc: HeaderCarrier) =
      verifiedEmailRepo.find(email) flatMap {
        case Some(verifiedEmail) => Future.successful(NoContent)
        case None => verifiedEmailRepo.insert(email) map (_ => Created)
      }
    val tokenNotFoundOrExpiredResponse = Future.successful(BadRequest(Json.toJson(ErrorResponse("TOKEN_NOT_FOUND_OR_EXPIRED", "Token not found or expired"))))

    implicit httpRequest =>
      withJsonBody[TokenVerificationRequest] { request =>
        tokenRepo.findToken(request.token) flatMap {
          case Some(doc) => createEmailIfNotExist(doc.email)
          case None => tokenNotFoundOrExpiredResponse
        }
      }
  }

  def verifiedEmail(email: String) = Action.async { implicit request =>
    verifiedEmailRepo.find(email).map {
      case Some(verifiedEmail) => Ok(toJson(verifiedEmail))
      case None => NotFound
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
