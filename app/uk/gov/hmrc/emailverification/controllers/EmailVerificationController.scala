/*
 * Copyright 2016 HM Revenue & Customs
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

import org.joda.time.Period
import org.joda.time.format.ISOPeriodFormat
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.mvc._
import uk.gov.hmrc.emailverification.connectors.EmailConnector
import uk.gov.hmrc.emailverification.repositories.VerificationTokenMongoRepository
import uk.gov.hmrc.emailverification.services.VerificationLinkService
import uk.gov.hmrc.play.http.Upstream4xxResponse
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

case class EmailVerificationRequest(email: String, templateId: String, templateParameters: Map[String, String], linkExpiryDuration: Period, continueUrl: String)

object EmailVerificationRequest {
  implicit val periodReads: Reads[Period] = JsPath.read[String].map(ISOPeriodFormat.standard().parsePeriod)
  implicit val reads: Reads[EmailVerificationRequest] = Json.reads[EmailVerificationRequest]
}

trait EmailVerificationController extends BaseController {
  def emailConnector: EmailConnector
  def verificationLinkService: VerificationLinkService
  def tokenRepo: VerificationTokenMongoRepository
  def newToken: String

  def requestVerification() = Action.async(parse.json) { implicit httpRequest =>
    def recovery: PartialFunction[Throwable, Result] = {
      case ex: Upstream4xxResponse => BadRequest(ex.message)
    }

    withJsonBody[EmailVerificationRequest] { request =>
      val token = newToken
      tokenRepo.insert(token, request.email, request.linkExpiryDuration)

      val paramsWithVerificationLink = request.templateParameters +
        ("verificationLink" -> verificationLinkService.verificationLinkFor(token, request.continueUrl))

      val sendResponse = emailConnector.sendEmail(request.email, request.templateId, paramsWithVerificationLink)

      sendResponse map (_ => NoContent) recover recovery
    }
  }
}

object EmailVerificationController extends EmailVerificationController {
  override lazy val emailConnector = EmailConnector
  override lazy val verificationLinkService = VerificationLinkService
  override lazy val tokenRepo = VerificationTokenMongoRepository()
  override def newToken = UUID.randomUUID().toString
}
