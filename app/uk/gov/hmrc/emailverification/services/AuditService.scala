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

package uk.gov.hmrc.emailverification.services

import javax.inject.Inject
import play.api.mvc.Request
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendHeaderCarrierProvider

import scala.concurrent.ExecutionContext

class AuditService @Inject() (
    auditConnector: AuditConnector
)(implicit ec: ExecutionContext) extends BackendHeaderCarrierProvider {

  def sendPinViaEmailEvent(emailAddress: String, passcode: String, serviceName: String, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "pinCode" -> passcode,
      "serviceName" -> serviceName,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value,
      "responseCode" -> responseCode.toString
    )

    sendEvent("SendEmailWithPin", details, "Email Verification - Send out PIN via Email")
  }

  def sendCheckEmailVerifiedEventSuccess(emailAddress: String, passcode: String, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "pinCode" -> passcode,
      "emailVerified" -> "true",
      "responseCode" -> responseCode.toString,
      "emailAddress" -> emailAddress
    )

    sendEvent("CheckEmailVerified", details, "Email Verification - Check Email is verified")
  }

  def sendCheckEmailVerifiedEventFailed(verifyFailureReason: String, passcode: String, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "pinCode" -> passcode,
      "emailVerified" -> "false",
      "responseCode" -> responseCode.toString,
      "verifyFailureReason" -> verifyFailureReason
    )

    sendEvent("CheckEmailVerified", details, "Email Verification - Check Email is verified")
  }

  private def sendEvent(auditType: String, details: Map[String, String], transactionName: String)(implicit request: Request[_]) = {
    val hcDetails = hc.toAuditDetails() ++ details

    val event = DataEvent(auditType   = auditType, tags = hc.toAuditTags(transactionName, request.path), detail = hcDetails, auditSource = "email-verification")
    auditConnector.sendEvent(event).map(_ => ())
  }

}

