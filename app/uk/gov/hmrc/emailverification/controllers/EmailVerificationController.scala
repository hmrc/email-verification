/*
 * Copyright 2019 HM Revenue & Customs
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

import config.AppConfig
import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.emailverification.connectors.{EmailConnector, PlatformAnalyticsConnector}
import uk.gov.hmrc.emailverification.models.{EmailVerificationRequest, ErrorResponse, GaEvents, TokenVerificationRequest}
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.emailverification.services.VerificationLinkService
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.{ExecutionContext, Future}


class EmailVerificationController @Inject() (emailConnector: EmailConnector,
                                             verificationLinkService: VerificationLinkService,
                                             tokenRepo:VerificationTokenMongoRepository,
                                             verifiedEmailRepo: VerifiedEmailMongoRepository,
                                             analyticsConnector: PlatformAnalyticsConnector,
                                             auditConnector: AuditConnector)(implicit ec:ExecutionContext, appConfig: AppConfig) extends BaseControllerWithJsonErrorHandling {

  def newToken() = UUID.randomUUID().toString

  private def sendEmailAndCreateVerification(request: EmailVerificationRequest)(implicit hc: HeaderCarrier) = {
    val token = newToken()
    val paramsWithVerificationLink = request.templateParameters.getOrElse(Map.empty) +
      ("verificationLink" -> verificationLinkService.verificationLinkFor(token, request.continueUrl))

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
          case false =>
            analyticsConnector.sendEvents(GaEvents.verificationRequested)
            sendEmailAndCreateVerification(request)
        } recover {
          case ex: BadRequestException =>
            val event = ExtendedDataEvent(
              auditSource =  "email-verification",
              auditType = "AIV-60",
              tags = hc.toAuditTags("requestVerification", httpRequest.path),
              detail = Json.obj(
                "email-address" -> request.email,
                "email-address-hex" -> toByteString(request.email)
              )
            )
            auditConnector.sendExtendedEvent(event)
            Logger.error("email-verification had a problem reading from repo", ex)
            BadRequest(Json.toJson(ErrorResponse("BAD_EMAIL_REQUEST", ex.getMessage)))
          case ex: NotFoundException =>
            Logger.error("email-verification had a problem, sendEmail returned not found", ex)
            Status(BAD_GATEWAY)(Json.toJson(ErrorResponse("UPSTREAM_ERROR", ex.getMessage)))
        }
      }
  }

  private def toByteString(data: String) : String = {
    data.getBytes("UTF-8").map("%02x".format(_)).mkString
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
          case Some(doc) =>
            analyticsConnector.sendEvents(GaEvents.verificationSuccess)
            createEmailIfNotExist(doc.email)
          case None =>
            analyticsConnector.sendEvents(GaEvents.verificationFailed)
            tokenNotFoundOrExpiredResponse
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

