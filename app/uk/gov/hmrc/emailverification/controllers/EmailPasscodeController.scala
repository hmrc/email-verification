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
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.emailverification.connectors.{EmailConnector, PlatformAnalyticsConnector}
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.{PasscodeMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


class EmailPasscodeController @Inject()(emailConnector: EmailConnector,
                                        passcodeRepo:PasscodeMongoRepository,
                                        verifiedEmailRepo: VerifiedEmailMongoRepository,
                                        analyticsConnector: PlatformAnalyticsConnector,
                                        auditConnector: AuditConnector,
                                        controllerComponents: ControllerComponents
                                            )(implicit ec:ExecutionContext, appConfig: AppConfig) extends BaseControllerWithJsonErrorHandling(controllerComponents) {

  def testOnlyGetPasscode(): Action[AnyContent] = Action.async{ implicit request =>
    hc.sessionId match {
      case Some(SessionId(id)) => passcodeRepo.findPasscodeBySessionId(id).map {
        case Some(passwordDoc) => Ok(Json.toJson(Passcode(passwordDoc.passcode)))
        case None => NotFound(Json.toJson(ErrorResponse("PASSCODE_NOT_FOUND_OR_EXPIRED", "No passcode found for sessionId")))
      }
      case None =>
        Future.successful(BadRequest(Json.toJson(ErrorResponse("BAD_PASSCODE_REQUEST", "No session id provided"))))
    }
  }

  private def newPasscode(): String =  {
    val codeSize = 6
    Random.alphanumeric
      .filterNot(_.isDigit)
      .filterNot(_.isLower)
      .filterNot(Set('A', 'E', 'I', 'O', 'U'))
      .take(codeSize).mkString
  }

  private def sendEmailAndStorePasscode(request: PasscodeRequest, sessionId: SessionId)(implicit hc: HeaderCarrier) = {
    val passcode = newPasscode()
    val paramsWithPasscode = request.templateParameters.getOrElse(Map.empty) +
      ("passcode" -> passcode)

    for {
      _ <- emailConnector.sendEmail(request.email, request.templateId, paramsWithPasscode)
      _ <- passcodeRepo.upsert(sessionId, passcode, request.email, request.linkExpiryDuration)
    } yield Created
  }

  def requestPasscode(): Action[JsValue] = Action.async(parse.json) {
    implicit httpRequest =>
      withJsonBody[PasscodeRequest] { request =>
        verifiedEmailRepo.isVerified(request.email) flatMap {
          case true => Future.successful(Conflict(Json.toJson(ErrorResponse("EMAIL_VERIFIED_ALREADY", "Email has already been verified"))))
          case false =>
            analyticsConnector.sendEvents(GaEvents.passcodeRequested)
            hc.sessionId match {
              case Some(sessionId) => sendEmailAndStorePasscode(request, sessionId)
              case None =>
                Future.successful(BadRequest(Json.toJson(ErrorResponse("BAD_EMAIL_REQUEST", "No session id provided"))))
            }

        } recover {
          case ex @ UpstreamErrorResponse(_, 400, _, _) =>
            val event = ExtendedDataEvent(
              auditSource =  "email-verification",
              auditType = "AIV-60",
              tags = hc.toAuditTags("requestPasscode", httpRequest.path),
              detail = Json.obj(
                "email-address" -> request.email,
                "email-address-hex" -> toByteString(request.email)
              )
            )
            auditConnector.sendExtendedEvent(event)
            Logger.error("email-verification had a problem reading from repo", ex)
            BadRequest(Json.toJson(ErrorResponse("BAD_EMAIL_REQUEST", ex.getMessage)))
          case ex @ UpstreamErrorResponse(_, 404, _, _)  =>
            Logger.error("email-verification had a problem, sendEmail returned not found", ex)
            Status(BAD_GATEWAY)(Json.toJson(ErrorResponse("UPSTREAM_ERROR", ex.getMessage)))
        }
      }
  }

  private def toByteString(data: String) : String = {
    data.getBytes("UTF-8").map("%02x".format(_)).mkString
  }

  private def storeEmailIfNotExist(email: String)(implicit hc: HeaderCarrier): Future[Result] = {
    verifiedEmailRepo.find(email) flatMap {
      case Some(verifiedEmail) => Future.successful(NoContent)
      case None => verifiedEmailRepo.insert(email) map (_ => Created)
    }
  }



  def verifyPasscode(): Action[JsValue] = Action.async(parse.json) { implicit request: Request[JsValue] => {
    withJsonBody[PasscodeVerificationRequest] { passcodeVerificationRequest: PasscodeVerificationRequest => {
      passcodeRepo.findPasscode(passcodeVerificationRequest.sessionId, passcodeVerificationRequest.passcode) flatMap {
        case Some(doc) =>
          analyticsConnector.sendEvents(GaEvents.passcodeSuccess)
          storeEmailIfNotExist(doc.email)
        case None =>
          analyticsConnector.sendEvents(GaEvents.passcodeFailed)
          Future.successful(BadRequest(Json.toJson(ErrorResponse("PASSCODE_NOT_FOUND_OR_EXPIRED", "Passcode not found or expired"))))
      }
    }}
  }}

}

