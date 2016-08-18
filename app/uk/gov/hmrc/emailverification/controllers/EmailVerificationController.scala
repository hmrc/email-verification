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

import org.joda.time.Period
import org.joda.time.format.ISOPeriodFormat
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.mvc._
import uk.gov.hmrc.emailverification.connectors.EmailConnector
import uk.gov.hmrc.emailverification.services.VerificationLinkService
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

object EmailVerificationController extends EmailVerificationController {
  override val emailConnector = EmailConnector
  override val verificationLinkService = VerificationLinkService
}

trait EmailVerificationController extends BaseController {

  val emailConnector: EmailConnector

  val verificationLinkService: VerificationLinkService

  def requestVerification() = Action.async(parse.json) { implicit httpRequest =>
    withJsonBody[EmailVerificationRequest] { request =>
      val paramsWithVerificationLink = request.templateParameters +
        ("verificationLink" -> verificationLinkService.createVericationLink())

      emailConnector.sendEmail(request.email, request.templateId, paramsWithVerificationLink) map (_ => NoContent)

    }
  }
}

case class EmailVerificationRequest(email: String, templateId: String, templateParameters: Map[String, String], linkExpiryDuration: Period, continueUrl: String)

object EmailVerificationRequest {
  implicit val durationReads: Reads[Period] = JsPath.read[String].map(ISOPeriodFormat.standard().parsePeriod)
  implicit val reads: Reads[EmailVerificationRequest] = Json.reads[EmailVerificationRequest]
}
