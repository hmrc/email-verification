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

import javax.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.emailverification.models.{VerificationStatusResponse, VerifyEmailRequest, VerifyEmailResponse}
import uk.gov.hmrc.emailverification.services.JourneyService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

class JourneyController @Inject() (
    controllerComponents: ControllerComponents,
    journeyService:       JourneyService
)(implicit ec: ExecutionContext)
  extends BackendController(controllerComponents) {

  def verifyEmail(): Action[VerifyEmailRequest] = Action.async(parse.json[VerifyEmailRequest]) { implicit request =>
    journeyService.initialise(request.body).map { redirectUrl =>
      Created(Json.toJson(VerifyEmailResponse(redirectUrl)))
    }
  }

  def completedEmails(credId: String): Action[AnyContent] = Action.async(parse.anyContent) { _ =>
    journeyService.findCompletedEmails(credId).map {
      case Nil    => NotFound(Json.obj("error" -> s"no verified or locked emails found for cred ID: $credId"))
      case emails => Ok(Json.toJson(VerificationStatusResponse(emails)))

    }
  }

}
