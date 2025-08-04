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

package uk.gov.hmrc.emailverification.controllers.testonly

import play.api.Logging
import play.api.libs.json.{JsArray, Json}
import play.api.mvc._
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.emailverification.controllers.BaseControllerWithJsonErrorHandling
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.{JourneyRepository, PasscodeMongoRepository}
import uk.gov.hmrc.http.SessionId

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TestOnlyEmailPasscodeController @Inject() (passcodeRepo: PasscodeMongoRepository,
                                                 controllerComponents: ControllerComponents,
                                                 val authConnector: AuthConnector,
                                                 journeyRepository: JourneyRepository
                                                )(implicit ec: ExecutionContext)
    extends BaseControllerWithJsonErrorHandling(controllerComponents)
    with AuthorisedFunctions
    with Logging {

  def testOnlyGetPasscodes(): Action[AnyContent] = Action.async { implicit request =>
    def journeyDocs(): Future[Seq[Journey]] = authorised().retrieve(Retrievals.credentials) {
      case Some(Credentials(credId, _)) =>
        journeyRepository.findByCredId(credId)
      case _ => Future(List())
    }

    hc.sessionId match {
      case Some(SessionId(id)) =>
        for {
          passcodeDocs <- passcodeRepo.findPasscodesBySessionId(id)
          journeyDocs  <- journeyDocs()
          emailPasscodes = passcodeDocs.map(d => EmailPasscode(d.email, d.passcode)) ++
                             journeyDocs.filter(_.emailAddress.isDefined).map(d => EmailPasscode(d.emailAddress.get, d.passcode))
        } yield
          if (emailPasscodes.isEmpty) NotFound(Json.toJson(ErrorResponse("PASSCODE_NOT_FOUND", "No passcode found for sessionId")))
          else
            Ok(Json.obj {
              "passcodes" -> JsArray(emailPasscodes.map {
                Json.toJson(_)
              })
            })
      case None =>
        Future.successful(Unauthorized(Json.toJson(ErrorResponse("NO_SESSION_ID", "No session id provided"))))
    }
  }

}
