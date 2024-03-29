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

package uk.gov.hmrc.emailverification.controllers

import config.AppConfig
import play.api.Logging
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.emailverification.connectors.EmailConnector
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.VerificationTokenMongoRepository
import uk.gov.hmrc.emailverification.services.{AuditService, VerificationLinkService, VerifiedEmailService}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EmailVerificationController @Inject() (
  emailConnector: EmailConnector,
  verificationLinkService: VerificationLinkService,
  tokenRepo: VerificationTokenMongoRepository,
  verifiedEmailService: VerifiedEmailService,
  auditConnector: AuditConnector,
  auditService: AuditService,
  controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends BaseControllerWithJsonErrorHandling(controllerComponents)
    with Logging {

  private def sendEmailAndCreateVerification(request: EmailVerificationRequest)(implicit hc: HeaderCarrier) = {
    val token = UUID.randomUUID().toString
    val paramsWithVerificationLink = request.templateParameters.getOrElse(Map.empty) +
      ("verificationLink" -> verificationLinkService.verificationLinkFor(token, request.continueUrl))

    for {
      _ <- emailConnector.sendEmail(request.email, request.templateId, paramsWithVerificationLink)
      _ <- tokenRepo.upsert(token, request.email, request.linkExpiryDuration)
    } yield Created
  }

  def requestVerification(): Action[JsValue] = Action.async(parse.json) { implicit httpRequest =>
    withJsonBody[EmailVerificationRequest] { request =>
      val mixedCaseEmail = request.email
      verifiedEmailService.isVerified(mixedCaseEmail) flatMap {
        case true => Future.successful(Conflict(Json.toJson(ErrorResponse("EMAIL_VERIFIED_ALREADY", "Email has already been verified"))))
        case false =>
          sendEmailAndCreateVerification(request).recover {
            case ex @ UpstreamErrorResponse(_, 400, _, _) =>
              val event = ExtendedDataEvent(
                auditSource = "email-verification",
                auditType = "AIV-60",
                tags = hc.toAuditTags("requestVerification", httpRequest.path),
                detail = Json.obj(
                  "email-address"     -> mixedCaseEmail,
                  "email-address-hex" -> toByteString(mixedCaseEmail)
                )
              )
              auditConnector.sendExtendedEvent(event)
              logger.error("email-verification had a problem, sendEmail returned bad request", ex)
              BadRequest(Json.toJson(ErrorResponse("BAD_EMAIL_REQUEST", ex.getMessage)))
            case ex @ UpstreamErrorResponse(_, 404, _, _) =>
              logger.error("email-verification had a problem, sendEmail returned not found", ex)
              Status(BAD_GATEWAY)(Json.toJson(ErrorResponse("UPSTREAM_ERROR", ex.getMessage)))
          }
      }
    }
  }

  private def toByteString(data: String): String = {
    data.getBytes("UTF-8").map("%02x".format(_)).mkString
  }

  def validateToken(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    def createEmailIfNotExist(email: String): Future[Result] =
      verifiedEmailService.find(email) flatMap {
        case Some(_) => Future.successful(NoContent)
        case None    => verifiedEmailService.insert(email) map (_ => Created)
      }

    withJsonBody[TokenVerificationRequest] { request =>
      tokenRepo.findToken(request.token) flatMap {
        case Some(doc) =>
          createEmailIfNotExist(doc.email)
        case None =>
          Future.successful(BadRequest(Json.toJson(ErrorResponse("TOKEN_NOT_FOUND_OR_EXPIRED", "Token not found or expired"))))
      }
    }
  }

  def verifiedEmail(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[VerifiedEmail] { verifiedEmail =>
      verifiedEmailService.find(mixedCaseEmail = verifiedEmail.email).map {
        case Some(email) =>
          auditService.sendCheckEmailVerifiedEvent(
            emailAddress = verifiedEmail.email,
            failureReason = None,
            responseCode = OK
          )
          Ok(toJson(email))
        case None =>
          auditService.sendCheckEmailVerifiedEvent(
            emailAddress = verifiedEmail.email,
            failureReason = Some("email address verification record not found"),
            responseCode = NOT_FOUND
          )
          NotFound
      }
    }
  }
}
