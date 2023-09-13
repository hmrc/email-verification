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

import play.api.Logging

import javax.inject.Inject
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions}
import uk.gov.hmrc.emailverification.models.{VerificationStatusResponse, VerifyEmailRequest, VerifyEmailResponse}
import uk.gov.hmrc.emailverification.services.{AuditService, EmailUpdateResult, JourneyService, PasscodeValidationResult, ResendPasscodeResult}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class JourneyController @Inject() (
    controllerComponents: ControllerComponents,
    journeyService:       JourneyService,
    val authConnector:    AuthConnector,
    auditService:         AuditService
)(implicit ec: ExecutionContext)
  extends BackendController(controllerComponents) with AuthorisedFunctions with Logging {

  def getJourney(journeyId: String): Action[AnyContent] = Action.async {
    journeyService.getJourney(journeyId).map {
      case Some(journey) =>
        Ok(Json.toJson(journey))
      case None =>
        NotFound
    }
  }

  def verifyEmail(): Action[VerifyEmailRequest] = Action.async(parse.json[VerifyEmailRequest]) { implicit request =>
    val req = request.body
    val verifyEmailRequest = req.copy(email = req.email.map(e => e.copy(address = e.address.toLowerCase)))
    journeyService.isLocked(verifyEmailRequest.credId, verifyEmailRequest.email.map(_.address)).flatMap{ locked =>
      if (locked) {
        auditService.sendVerifyEmailRequestReceivedEvent(verifyEmailRequest, 401)
        Future.successful(Unauthorized)
      } else {
        journeyService.checkIfEmailExceedsCount(
          verifyEmailRequest.credId,
          verifyEmailRequest.email.map(_.address).getOrElse("")
        ).flatMap { emailExceedsCount =>
          if (emailExceedsCount) {
            auditService.sendVerifyEmailRequestReceivedEvent(verifyEmailRequest, 401)
            Future.successful(Unauthorized)
          } else {
            journeyService.initialise(verifyEmailRequest).map { redirectUrl =>
              auditService.sendVerifyEmailRequestReceivedEvent(verifyEmailRequest, 201)
              Created(Json.toJson(VerifyEmailResponse(redirectUrl)))
            }.recover {
              case ex: UpstreamErrorResponse =>
                auditService.sendVerifyEmailRequestReceivedEvent(verifyEmailRequest, ex.reportAs)
                Status(ex.reportAs)(ex.getMessage())
            }
          }
        }
      }
    }
  }

  def submitEmail(journeyId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    (request.body \ "email").asOpt[String] match {
      case Some(value) =>
        val email = value.toLowerCase
        journeyService.submitEmail(journeyId, email).map {
          case EmailUpdateResult.Accepted =>
            Ok(Json.obj("status" -> "accepted"))
          case EmailUpdateResult.TooManyAttempts(continueUrl) =>
            Forbidden(Json.obj(
              "status" -> "tooManyAttempts",
              "continueUrl" -> continueUrl
            ))
          case EmailUpdateResult.JourneyNotFound =>
            NotFound(Json.obj("status" -> "journeyNotFound"))
        }
      case None =>
        Future.successful(BadRequest)
    }
  }

  def resendPasscode(journeyId: String): Action[AnyContent] = Action.async { implicit request =>
    journeyService.resendPasscode(journeyId).map {
      case ResendPasscodeResult.PasscodeResent =>
        Ok(Json.obj(
          "status" -> "passcodeResent"
        ))
      case ResendPasscodeResult.JourneyNotFound =>
        NotFound(Json.obj(
          "status" -> "journeyNotFound"
        ))
      case ResendPasscodeResult.NoEmailProvided =>
        Forbidden(Json.obj(
          "status" -> "noEmailProvided"
        ))
      case ResendPasscodeResult.TooManyAttemptsForEmail(journey) =>
        Forbidden(Json.obj(
          "status" -> "tooManyAttemptsForEmail",
          "journey" -> journey
        ))
      case ResendPasscodeResult.TooManyAttemptsInSession(continueUrl) =>
        Forbidden(Json.obj(
          "status" -> "tooManyAttemptsInSession",
          "continueUrl" -> continueUrl
        ))
    }
  }

  def submitPasscode(journeyId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authorised().retrieve(Retrievals.credentials) {
      case Some(Credentials(credId, _)) =>
        (request.body \ "passcode").asOpt[String] match {
          case Some(passcode) =>
            journeyService.validatePasscode(journeyId, credId, passcode).map {
              case PasscodeValidationResult.Complete(redirectUri) =>
                Ok(Json.obj(
                  "status" -> "complete",
                  "continueUrl" -> redirectUri
                ))
              case PasscodeValidationResult.IncorrectPasscode(journey) =>
                BadRequest(Json.obj(
                  "status" -> "incorrectPasscode",
                  "journey" -> journey
                ))
              case PasscodeValidationResult.TooManyAttempts(continueUrl) =>
                Forbidden(Json.obj(
                  "status" -> "tooManyAttempts",
                  "continueUrl" -> continueUrl
                ))
              case PasscodeValidationResult.JourneyNotFound =>
                NotFound(Json.obj("status" -> "journeyNotFound"))
            }
          case None =>
            Future.successful(BadRequest)
        }
      case None =>
        Future.successful(Unauthorized)
    }.recover {
      case _: AuthorisationException =>
        Unauthorized
    }
  }

  def completedEmails(credId: String): Action[AnyContent] = Action.async(parse.anyContent) { implicit request =>
    journeyService.findCompletedEmails(credId).map {
      case Nil =>
        auditService.sendEmailVerificationOutcomeRequestEvent(credId, JsArray(), 404)
        NotFound(Json.obj("error" -> s"no verified or locked emails found for cred ID: $credId"))
      case emails =>
        auditService.sendEmailVerificationOutcomeRequestEvent(credId, Json.toJson(emails).as[JsArray], 200)
        Ok(Json.toJson(VerificationStatusResponse(emails)))
    }
  }

}
