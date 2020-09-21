/*
 * Copyright 2020 HM Revenue & Customs
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
import javax.inject.Inject
import play.api.Logging
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.emailverification.connectors.{EmailConnector, PlatformAnalyticsConnector}
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.{PasscodeMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.emailverification.services.AuditService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class EmailPasscodeController @Inject() (
    emailConnector:       EmailConnector,
    passcodeRepo:         PasscodeMongoRepository,
    verifiedEmailRepo:    VerifiedEmailMongoRepository,
    analyticsConnector:   PlatformAnalyticsConnector,
    auditConnector:       AuditConnector,
    controllerComponents: ControllerComponents,
    auditService:         AuditService
)(implicit ec: ExecutionContext, appConfig: AppConfig) extends BaseControllerWithJsonErrorHandling(controllerComponents) with Logging {

  def testOnlyGetPasscodes(): Action[AnyContent] = Action.async { implicit request =>
    hc.sessionId match {
      case Some(SessionId(id)) => passcodeRepo.findPasscodesBySessionId(id).map {
        case passcodes: List[PasscodeDoc] if passcodes.isEmpty => NotFound(Json.toJson(ErrorResponse("PASSCODE_NOT_FOUND_OR_EXPIRED", "No passcode found for sessionId")))
        case passcodes: List[PasscodeDoc] => Ok(Json.obj{
          "passcodes" -> JsArray(passcodes.map { passcodeDoc =>
            Json.toJson(EmailPasscode(passcodeDoc.email, passcodeDoc.passcode))
          })
        })
      }
      case None =>
        Future.successful(Unauthorized(Json.toJson(ErrorResponse("NO_SESSION_ID", "No session id provided"))))
    }
  }

  private def newPasscode(): String = {
    val codeSize = 6
    Random.alphanumeric
      .filterNot(_.isDigit)
      .filterNot(_.isLower)
      .filterNot(Set('A', 'E', 'I', 'O', 'U'))
      .take(codeSize).mkString
  }

  private def sendEmail(email: String, passcode: String, serviceName: String)(implicit request: Request[_]) = {
    val paramsWithPasscode = appConfig.passcodeEmailTemplateParameters +
      ("passcode" -> passcode, "team_name" -> serviceName)
    emailConnector.sendEmail(to         = email, templateId = "email_verification_passcode", params = paramsWithPasscode).map { emailResponse =>
      auditService.sendPinViaEmailEvent(
        emailAddress = email,
        passcode     = passcode,
        serviceName  = serviceName,
        responseCode = emailResponse.status
      )
      ()
    }.recoverWith {
      case e: UpstreamErrorResponse =>
        auditService.sendPinViaEmailEvent(
          emailAddress = email,
          passcode     = passcode,
          serviceName  = serviceName,
          responseCode = e.statusCode
        )
        Future.failed(e)
    }
  }

  case object EmailAlreadyVerified extends Exception("email address has already been verified")
  case object MissingSessionId extends Exception("missing session id")
  case object MaxDifferentEmailsExceeded extends Exception("Maximum permitted number of emails sent to different addresses reached")
  case object MaxEmailsToAddressExceeded extends Exception("Maximum permitted number of emails sent to same address reached")

  def requestPasscode(): Action[JsValue] = Action.async(parse.json) {
    implicit httpRequest =>
      withJsonBody[PasscodeRequest] { request =>
        (for {
          emailAlreadyVerified <- verifiedEmailRepo.isVerified(request.email)
          _ <- if (!emailAlreadyVerified) Future.unit else Future.failed(EmailAlreadyVerified)
          _ = analyticsConnector.sendEvents(GaEvents.passcodeRequested)
          sessionId <- hc.sessionId.fold[Future[SessionId]](Future.failed(MissingSessionId))(Future.successful(_))
          sessionEmailCount <- passcodeRepo.getSessionEmailsCount(sessionId)
          _ <- if (sessionEmailCount < appConfig.maxDifferentEmails) Future.unit else Future.failed(MaxDifferentEmailsExceeded)
          passcode: String = newPasscode()
          passcodeDoc <- passcodeRepo.upsertIncrementingEmailAttempts(sessionId, passcode, request.email, appConfig.passcodeExpiryMinutes)
          _ <- if (passcodeDoc.emailAttempts <= appConfig.maxEmailAttempts) Future.unit else Future.failed(MaxEmailsToAddressExceeded)
          _ <- sendEmail(request.email, passcode, request.serviceName)
        } yield {
          Created
        }).recover {
          case MissingSessionId => {
            val msg = "No session id provided"
            Unauthorized(Json.toJson(ErrorResponse("NO_SESSION_ID", msg)))
          }
          case EmailAlreadyVerified => {
            val msg = "Email has already been verified"
            Conflict(Json.toJson(ErrorResponse("EMAIL_VERIFIED_ALREADY", msg)))
          }
          case MaxDifferentEmailsExceeded => {
            val msg = s"Max permitted number of different email addresses used per session of ${appConfig.maxDifferentEmails} reached"
            Forbidden(Json.toJson(ErrorResponse("MAX_EMAILS_EXCEEDED", msg)))
          }
          case MaxEmailsToAddressExceeded => {
            val msg = s"Max permitted number of emails sent to the same address of ${appConfig.maxEmailAttempts} reached"
            Forbidden(Json.toJson(ErrorResponse("MAX_EMAILS_EXCEEDED", msg)))
          }
          case ex @ UpstreamErrorResponse(_, 400, _, _) =>
            val event = ExtendedDataEvent(
              auditSource = "email-verification",
              auditType   = "AIV-60",
              tags        = hc.toAuditTags("requestPasscode", httpRequest.path),
              detail      = Json.obj(
                "email-address" -> request.email,
                "email-address-hex" -> request.email.getBytes("UTF-8").map("%02x".format(_)).mkString
              )
            )
            auditConnector.sendExtendedEvent(event)
            logger.error("email-verification had a problem, sendEmail returned bad request", ex)
            BadRequest(Json.toJson(ErrorResponse("BAD_EMAIL_REQUEST", ex.getMessage)))
          case ex @ UpstreamErrorResponse(_, 404, _, _) =>
            logger.error("email-verification had a problem, sendEmail returned not found", ex)
            BadGateway(Json.toJson(ErrorResponse("UPSTREAM_ERROR", ex.getMessage)))
        }
      }
  }

  def verifyPasscode(): Action[JsValue] = Action.async(parse.json) { implicit request: Request[JsValue] =>
    {
      withJsonBody[PasscodeVerificationRequest] { passcodeVerificationRequest: PasscodeVerificationRequest =>
        {

          hc.sessionId match {
            case Some(id: SessionId) => {
              passcodeRepo.findPasscodeAndIncrementAttempts(id, passcodeVerificationRequest.email).flatMap {
                case Some(doc: PasscodeDoc) if doc.passcodeAttempts > appConfig.maxPasscodeAttempts => {
                  val msg = s"Max permitted passcode verification attempts per session of ${appConfig.maxPasscodeAttempts} reached"
                  Future.successful(Forbidden(Json.toJson(ErrorResponse("MAX_PASSCODE_ATTEMPTS_EXCEEDED", msg))))
                }
                case Some(doc: PasscodeDoc) if doc.passcode == passcodeVerificationRequest.passcode.toUpperCase =>
                  analyticsConnector.sendEvents(GaEvents.passcodeSuccess)
                  verifiedEmailRepo.find(doc.email) flatMap {
                    case None =>
                      auditService.sendCheckEmailVerifiedEventSuccess(
                        emailAddress = doc.email,
                        passcode     = passcodeVerificationRequest.passcode,
                        responseCode = 201
                      )
                      verifiedEmailRepo.insert(doc.email) map (_ => Created)
                    case _ =>
                      auditService.sendCheckEmailVerifiedEventSuccess(
                        emailAddress = doc.email,
                        passcode     = passcodeVerificationRequest.passcode,
                        responseCode = 204
                      )
                      Future.successful(NoContent)
                  }

                case _ =>
                  analyticsConnector.sendEvents(GaEvents.passcodeFailed)
                  val message = "Passcode not found or expired"
                  auditService.sendCheckEmailVerifiedEventFailed(
                    verifyFailureReason = message,
                    passcode            = passcodeVerificationRequest.passcode,
                    responseCode        = 404
                  )
                  Future.successful(NotFound(Json.toJson(ErrorResponse("PASSCODE_NOT_FOUND_OR_EXPIRED", message))))
              }
            }
            case None =>
              val message = "No session id provided"
              auditService.sendCheckEmailVerifiedEventFailed(
                verifyFailureReason = message,
                passcode            = passcodeVerificationRequest.passcode,
                responseCode        = 401
              )
              Future.successful(Unauthorized(Json.toJson(ErrorResponse("NO_SESSION_ID", message))))
          }
        }
      }
    }
  }
}

