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

package uk.gov.hmrc.emailverification.controllers

import play.api.Logging

import javax.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions}
import uk.gov.hmrc.emailverification.models.{VerificationStatusResponse, VerifyEmailRequest, VerifyEmailResponse}
import uk.gov.hmrc.emailverification.services.{EmailUpdateResult, JourneyService, PasscodeValidationResult, ResendPasscodeResult}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class JourneyController @Inject() (
    controllerComponents: ControllerComponents,
    journeyService:       JourneyService,
    val authConnector:    AuthConnector
)(implicit ec: ExecutionContext)
  extends BackendController(controllerComponents) with AuthorisedFunctions with Logging {

  def getJourney(journeyId: String): Action[AnyContent] = Action.async {
    journeyService.getJourney(journeyId).map {
      case Some(journey) =>
        // don't give the frontend data it doesn't need
        Ok(Json.obj(
          "accessibilityStatementUrl" -> journey.accessibilityStatementUrl,
          "emailEnterUrl" -> journey.emailEnterUrl
        ))
      case None =>
        NotFound
    }
  }

  def verifyEmail(): Action[VerifyEmailRequest] = Action.async(parse.json[VerifyEmailRequest]) { implicit request =>
    journeyService.initialise(request.body).map { redirectUrl =>
      Created(Json.toJson(VerifyEmailResponse(redirectUrl)))
    }
  }

  def submitEmail(journeyId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    (request.body \ "email").asOpt[String] match {
      case Some(email) =>
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
      case ResendPasscodeResult.TooManyAttemptsForEmail(enterEmailUrl) =>
        Forbidden(Json.obj(
          "status" -> "tooManyAttemptsForEmail",
          "enterEmailUrl" -> enterEmailUrl
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
              case PasscodeValidationResult.IncorrectPasscode(enterEmailUrl) =>
                BadRequest(Json.obj(
                  "status" -> "incorrectPasscode",
                  "enterEmailUrl" -> enterEmailUrl
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

  def completedEmails(credId: String): Action[AnyContent] = Action.async(parse.anyContent) { _ =>
    journeyService.findCompletedEmails(credId).map {
      case Nil    => NotFound(Json.obj("error" -> s"no verified or locked emails found for cred ID: $credId"))
      case emails => Ok(Json.toJson(VerificationStatusResponse(emails)))
    }
  }

}
