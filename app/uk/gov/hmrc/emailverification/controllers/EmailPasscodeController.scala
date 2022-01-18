/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.emailverification.connectors.{EmailConnector, PlatformAnalyticsConnector}
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.{JourneyRepository, PasscodeMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.emailverification.services.AuditService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class EmailPasscodeController @Inject() (
    emailConnector:       EmailConnector,
    passcodeRepo:         PasscodeMongoRepository,
    verifiedEmailRepo:    VerifiedEmailMongoRepository,
    analyticsConnector:   PlatformAnalyticsConnector,
    auditConnector:       AuditConnector,
    controllerComponents: ControllerComponents,
    auditService:         AuditService,
    val authConnector:    AuthConnector,
    journeyRepository:    JourneyRepository)(implicit ec: ExecutionContext, appConfig: AppConfig)
  extends BaseControllerWithJsonErrorHandling(controllerComponents) with AuthorisedFunctions with Logging {

  def testOnlyGetPasscodes(): Action[AnyContent] = Action.async { implicit request =>
      def journeyDocs() = authorised().retrieve(Retrievals.credentials) {
        case Some(Credentials(credId, _)) =>
          journeyRepository.findByCredId(credId)
        case _ => Future(List())
      }

    hc.sessionId match {
      case Some(SessionId(id)) =>
        for {
          passcodeDocs <- passcodeRepo.findPasscodesBySessionId(id)
          journeyDocs <- journeyDocs()
          emailPasscodes = passcodeDocs.map(d => EmailPasscode(d.email, d.passcode)) ++
            journeyDocs.filter(_.emailAddress.isDefined).map(d => EmailPasscode(d.emailAddress.get, d.passcode))
        } yield if (emailPasscodes.isEmpty) NotFound(Json.toJson(ErrorResponse("PASSCODE_NOT_FOUND", "No passcode found for sessionId")))
        else Ok(Json.obj {
          "passcodes" -> JsArray(emailPasscodes.map {
            Json.toJson(_)
          })
        })
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

  private def sendEmail(passcodeRequest: PasscodeRequest, passcode: String)(implicit request: Request[_]) = {

    val templateId = passcodeRequest.lang match {
      case English => "email_verification_passcode"
      case Welsh   => "email_verification_passcode_welsh"
    }

    val paramsWithPasscode = appConfig.passcodeEmailTemplateParameters +
      ("passcode" -> passcode, "team_name" -> passcodeRequest.serviceName)
    emailConnector.sendEmail(to         = passcodeRequest.email, templateId = templateId, params = paramsWithPasscode).map { emailResponse =>
      auditService.sendPasscodeViaEmailEvent(
        emailAddress = passcodeRequest.email,
        passcode     = passcode,
        serviceName  = passcodeRequest.serviceName,
        responseCode = emailResponse.status
      )
      ()
    }.recoverWith {
      case e: UpstreamErrorResponse =>
        auditService.sendPasscodeViaEmailEvent(
          emailAddress = passcodeRequest.email,
          passcode     = passcode,
          serviceName  = passcodeRequest.serviceName,
          responseCode = e.statusCode
        )
        Future.failed(e)
    }
  }

  case object EmailAlreadyVerified extends Exception("email address has already been verified")

  case object MissingSessionId extends Exception("missing session id")

  case class MaxDifferentEmailsExceeded(differentEmailsCount: Long) extends Exception("Maximum permitted number of emails sent to different addresses reached")

  case class MaxEmailsToAddressExceeded(differentEmailsCount: Long, passcodeDoc: PasscodeDoc) extends Exception("Maximum permitted number of emails sent to same address reached")

  case class SendEmailReturnedBadRequest(differentEmailsCount: Long, passcodeDoc: PasscodeDoc, ex: UpstreamErrorResponse) extends Exception("sendEmail failed with BadRequest")

  case class SendEmailReturnedNotFound(differentEmailsCount: Long, passcodeDoc: PasscodeDoc, ex: UpstreamErrorResponse) extends Exception("sendEmail failed with NotFound")

  def requestPasscode(): Action[JsValue] = Action.async(parse.json) {
    implicit httpRequest =>
      withJsonBody[PasscodeRequest] { request =>
        (for {
          emailAlreadyVerified <- verifiedEmailRepo.isVerified(request.email)
          _ <- if (emailAlreadyVerified) Future.failed(EmailAlreadyVerified) else Future.unit
          _ = analyticsConnector.sendEvents(GaEvents.passcodeRequested)
          sessionId <- hc.sessionId.fold[Future[SessionId]](Future.failed(MissingSessionId))(Future.successful)
          sessionEmailCount <- passcodeRepo.getSessionEmailsCount(sessionId)
          _ <- if (sessionEmailCount < appConfig.maxDifferentEmails) Future.unit else Future.failed(MaxDifferentEmailsExceeded(sessionEmailCount))
          passcode = newPasscode()
          passcodeDoc <- passcodeRepo.upsertIncrementingEmailAttempts(sessionId, passcode, request.email, appConfig.passcodeExpiryMinutes)
          _ <- if (passcodeDoc.emailAttempts <= appConfig.maxAttemptsPerEmail) Future.unit else Future.failed(MaxEmailsToAddressExceeded(sessionEmailCount, passcodeDoc))
          _ <- sendEmail(request, passcode).recoverWith { //transforming the exception here to add in sessionEmailCount, passcodeDoc wanted for audit events
            case ex @ UpstreamErrorResponse(_, 400, _, _) => {
              logger.error("email-verification had a problem, sendEmail returned bad request", ex)
              Future.failed(SendEmailReturnedBadRequest(sessionEmailCount, passcodeDoc, ex))
            }
            case ex @ UpstreamErrorResponse(_, _, _, _) => {
              logger.error("email-verification had a problem, sendEmail returned not found", ex)
              Future.failed(SendEmailReturnedNotFound(sessionEmailCount, passcodeDoc, ex))
            }
          }
        } yield {
          auditService.sendEmailPasscodeRequestSuccessfulEvent(request.email, passcode, request.serviceName, sessionEmailCount, passcodeDoc, CREATED)
          Created
        }).recover {
          case MissingSessionId => {
            auditService.sendEmailRequestMissingSessionIdEvent(request.email, UNAUTHORIZED)
            val msg = "No session id provided"
            Unauthorized(Json.toJson(ErrorResponse("NO_SESSION_ID", msg)))
          }
          case EmailAlreadyVerified => {
            auditService.sendEmailAddressAlreadyVerifiedEvent(request.email, request.serviceName, CONFLICT)
            val msg = "Email has already been verified"
            Conflict(Json.toJson(ErrorResponse("EMAIL_VERIFIED_ALREADY", msg)))
          }
          case MaxDifferentEmailsExceeded(differentEmailsCount) => {
            auditService.sendMaxDifferentEmailsExceededEvent(request.email, request.serviceName, differentEmailsCount, FORBIDDEN)
            val msg = s"Max permitted number of different email addresses used per session of ${appConfig.maxDifferentEmails} reached"
            Forbidden(Json.toJson(ErrorResponse("MAX_EMAILS_EXCEEDED", msg)))
          }
          case MaxEmailsToAddressExceeded(differentEmailsCount, passcodeDoc) => {
            auditService.sendMaxEmailsExceededEvent(request.email, request.serviceName, differentEmailsCount, passcodeDoc, FORBIDDEN)
            val msg = s"Max permitted number of emails sent to the same address of ${appConfig.maxAttemptsPerEmail} reached"
            Forbidden(Json.toJson(ErrorResponse("MAX_EMAILS_EXCEEDED", msg)))
          }
          case SendEmailReturnedBadRequest(sessionEmailCount, passcodeDoc, ex) => {
            auditService.sendPasscodeEmailDeliveryErrorEvent(request.email, request.serviceName, sessionEmailCount, passcodeDoc, BAD_REQUEST)
            logger.error("email-verification had a problem, sendEmail returned bad request", ex)
            BadRequest(Json.toJson(ErrorResponse("BAD_EMAIL_REQUEST", ex.getMessage)))
          }
          case SendEmailReturnedNotFound(sessionEmailCount, passcodeDoc, ex) => {
            auditService.sendPasscodeEmailDeliveryErrorEvent(request.email, request.serviceName, sessionEmailCount, passcodeDoc, BAD_GATEWAY)
            logger.error("email-verification had a problem, sendEmail returned not found", ex)
            BadGateway(Json.toJson(ErrorResponse("UPSTREAM_ERROR", ex.getMessage)))
          }
        }
      }
  }

  def verifyPasscode(): Action[JsValue] = Action.async(parse.json) { implicit request: Request[JsValue] =>
    withJsonBody[PasscodeVerificationRequest] { passcodeVerificationRequest =>
      hc.sessionId match {
        case Some(id) => {
          passcodeRepo.findPasscodeAndIncrementAttempts(id, passcodeVerificationRequest.email).flatMap {
            case Some(doc) if doc.passcodeAttempts > appConfig.maxPasscodeAttempts =>
              auditService.sendMaxPasscodeAttemptsExceededEvent(
                emailAddress = passcodeVerificationRequest.email,
                passcode     = passcodeVerificationRequest.passcode,
                passcodeDoc  = doc,
                responseCode = FORBIDDEN
              )
              val msg = s"Max permitted passcode verification attempts per session of ${appConfig.maxPasscodeAttempts} reached"
              Future.successful(Forbidden(Json.toJson(ErrorResponse("MAX_PASSCODE_ATTEMPTS_EXCEEDED", msg))))

            case Some(doc) if doc.passcode == passcodeVerificationRequest.passcode.toUpperCase =>
              analyticsConnector.sendEvents(GaEvents.passcodeSuccess)
              verifiedEmailRepo.find(doc.email) flatMap {
                case None =>
                  auditService.sendEmailAddressConfirmedEvent(
                    emailAddress = passcodeVerificationRequest.email,
                    passcode     = passcodeVerificationRequest.passcode,
                    passcodeDoc  = doc,
                    responseCode = CREATED)
                  verifiedEmailRepo.insert(doc.email) map (_ => Created)
                case _ =>
                  auditService.sendEmailAddressConfirmedEvent(
                    emailAddress = passcodeVerificationRequest.email,
                    passcode     = passcodeVerificationRequest.passcode,
                    passcodeDoc  = doc,
                    responseCode = NO_CONTENT)
                  Future.successful(NoContent)
              }

            case Some(doc) =>
              analyticsConnector.sendEvents(GaEvents.passcodeFailed)
              auditService.sendPasscodeMatchNotFoundOrExpiredEvent(
                emailAddress = passcodeVerificationRequest.email,
                passcode     = passcodeVerificationRequest.passcode,
                passcodeDoc  = doc,
                responseCode = NOT_FOUND
              )
              Future.successful(NotFound(Json.obj(
                "code" -> "PASSCODE_MISMATCH",
                "message" -> "Passcode mismatch"
              )))

            case _ =>
              analyticsConnector.sendEvents(GaEvents.passcodeFailed)
              val message = "Passcode not found"
              auditService.sendEmailAddressNotFoundOrExpiredEvent(
                emailAddress = passcodeVerificationRequest.email,
                passcode     = passcodeVerificationRequest.passcode,
                responseCode = NOT_FOUND
              )
              Future.successful(NotFound(Json.toJson(ErrorResponse("PASSCODE_NOT_FOUND", message))))
          }
        }
        case None =>
          val message = "No session id provided"
          auditService.sendVerificationRequestMissingSessionIdEvent(
            emailAddress = passcodeVerificationRequest.email,
            passcode     = passcodeVerificationRequest.passcode,
            responseCode = UNAUTHORIZED
          )
          Future.successful(Unauthorized(Json.toJson(ErrorResponse("NO_SESSION_ID", message))))
      }
    }
  }
}

