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
import play.api.libs.json.{JsValue, Json}
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

  def testOnlyGetPasscode(): Action[AnyContent] = Action.async { implicit request =>
    hc.sessionId match {
      case Some(SessionId(id)) => passcodeRepo.findPasscodeBySessionId(id).map {
        case Some(passwordDoc) => Ok(Json.toJson(Passcode(passwordDoc.passcode)))
        case None              => NotFound(Json.toJson(ErrorResponse("PASSCODE_NOT_FOUND_OR_EXPIRED", "No passcode found for sessionId")))
      }
      case None =>
        Future.successful(BadRequest(Json.toJson(ErrorResponse("BAD_PASSCODE_REQUEST", "No session id provided"))))
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

  private def sendEmailAndStorePasscode(passcodeRequest: PasscodeRequest, sessionId: SessionId)(implicit request: Request[_]) = {
    val passcode = newPasscode()
    val paramsWithPasscode = appConfig.passcodeEmailTemplateParameters +
      ("passcode" -> passcode, "team_name" -> passcodeRequest.serviceName)

    val sendEmailAndStorePasscode = for {
      emailResponse <- emailConnector.sendEmail(to         = passcodeRequest.email, templateId = "email_verification_passcode", params = paramsWithPasscode)
      _ <- passcodeRepo.upsert(sessionId, passcode, passcodeRequest.email, appConfig.passcodeExpiryMinutes)
    } yield {
      auditService.sendPinViaEmailEvent(
        emailAddress = passcodeRequest.email,
        passcode     = passcode,
        serviceName  = passcodeRequest.serviceName,
        responseCode = emailResponse.status
      )
    }

    sendEmailAndStorePasscode recoverWith {
      case e: UpstreamErrorResponse =>
        auditService.sendPinViaEmailEvent(
          emailAddress = passcodeRequest.email,
          passcode     = passcode,
          serviceName  = passcodeRequest.serviceName,
          responseCode = e.statusCode
        )
        Future.failed(e)
    }
  }

  def requestPasscode(): Action[JsValue] = Action.async(parse.json) {
    implicit httpRequest =>
      withJsonBody[PasscodeRequest] { request =>
        verifiedEmailRepo.isVerified(request.email) flatMap {
          case true => Future.successful(Conflict(Json.toJson(ErrorResponse("EMAIL_VERIFIED_ALREADY", "Email has already been verified"))))
          case false =>
            analyticsConnector.sendEvents(GaEvents.passcodeRequested)
            hc.sessionId match {
              case Some(sessionId) => {
                sendEmailAndStorePasscode(request, sessionId).map(_ => Created).recover {
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
              case None =>
                Future.successful(BadRequest(Json.toJson(ErrorResponse("BAD_REQUEST", "No session id provided"))))
            }

        }
      }
  }

  def verifyPasscode(): Action[JsValue] = Action.async(parse.json) { implicit request: Request[JsValue] =>
    {
      withJsonBody[PasscodeVerificationRequest] { passcodeVerificationRequest: PasscodeVerificationRequest =>
        {

          hc.sessionId match {
            case Some(SessionId(id)) =>
              passcodeRepo.findPasscode(id, passcodeVerificationRequest.passcode) flatMap {
                case Some(doc) =>
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

                case None =>
                  analyticsConnector.sendEvents(GaEvents.passcodeFailed)
                  val message = "Passcode not found or expired"
                  auditService.sendCheckEmailVerifiedEventFailed(
                    verifyFailureReason = message,
                    passcode            = passcodeVerificationRequest.passcode,
                    responseCode        = 400
                  )
                  Future.successful(BadRequest(Json.toJson(ErrorResponse("PASSCODE_NOT_FOUND_OR_EXPIRED", message))))
              }
            case None =>
              val message = "No session id provided"
              auditService.sendCheckEmailVerifiedEventFailed(
                verifyFailureReason = message,
                passcode            = passcodeVerificationRequest.passcode,
                responseCode        = 400
              )
              Future.successful(BadRequest(Json.toJson(ErrorResponse("NO_SESSION_ID", message))))
          }
        }
      }
    }
  }
}

