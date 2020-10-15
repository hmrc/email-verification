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
import uk.gov.hmrc.emailverification.models.PasscodeDoc
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendHeaderCarrierProvider

import scala.concurrent.ExecutionContext

class AuditService @Inject() (
    auditConnector: AuditConnector
)(implicit ec: ExecutionContext) extends BackendHeaderCarrierProvider {

  def sendPasscodeViaEmailEvent(emailAddress: String, passcode: String, serviceName: String, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> passcode,
      "serviceName" -> serviceName,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value,
      "responseCode" -> responseCode.toString
    )

    sendEvent("SendEmailWithPasscode", details, "HMRC Gateway - Email Verification - Send out passcode via Email")
  }

  def sendCheckEmailVerifiedEvent(emailAddress: String, failureReason: Option[String], responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "emailVerified" -> failureReason.fold("true")(_ => "false"),
      "responseCode" -> responseCode.toString,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    ) ++ failureReason.map("verifyFailureReason" -> _).toMap

    sendEvent("CheckEmailVerified", details, "HMRC Gateway - Email Verification - Check Email is verified")
  }

/*********** PasscodeVerificationRequest Events *************/

  def sendEmailRequestMissingSessionIdEvent(emailAddress: String, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> "-",
      "passcodeAttempts" -> "-",
      "sameEmailAttempts" -> "-",
      "differentEmailAttempts" -> "-",
      "success" -> "false",
      "responseCode" -> responseCode.toString,
      "outcome" -> "SessionId missing",
      "callingService" -> "-",
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

  def sendEmailAddressAlreadyVerifiedEvent(emailAddress: String, callingService: String, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> "-",
      "passcodeAttempts" -> "-",
      "sameEmailAttempts" -> "-",
      "differentEmailAttempts" -> "-",
      "success" -> "true",
      "responseCode" -> responseCode.toString,
      "outcome" -> "Email address already confirmed",
      "callingService" -> callingService,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

  def sendMaxEmailsExceededEvent(emailAddress: String, callingService: String, differentEmailAttempts: Long, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> "-",
      "passcodeAttempts" -> passcodeDoc.passcodeAttempts.toString,
      "sameEmailAttempts" -> passcodeDoc.emailAttempts.toString,
      "differentEmailAttempts" -> differentEmailAttempts.toString,
      "success" -> "false",
      "responseCode" -> responseCode.toString,
      "outcome" -> "Max permitted passcode emails per session has been exceeded",
      "callingService" -> callingService,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

  def sendMaxDifferentEmailsExceededEvent(emailAddress: String, callingService: String, differentEmailAttempts: Long, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> "-",
      "passcodeAttempts" -> "-",
      "sameEmailAttempts" -> "-",
      "differentEmailAttempts" -> differentEmailAttempts.toString,
      "success" -> "false",
      "responseCode" -> responseCode.toString,
      "outcome" -> "Max permitted passcode emails per session has been exceeded",
      "callingService" -> callingService,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

  def sendPasscodeEmailDeliveryErrorEvent(emailAddress: String, callingService: String, differentEmailAttempts: Long, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> "-",
      "passcodeAttempts" -> passcodeDoc.passcodeAttempts.toString,
      "sameEmailAttempts" -> passcodeDoc.emailAttempts.toString,
      "differentEmailAttempts" -> differentEmailAttempts.toString,
      "success" -> "false",
      "responseCode" -> responseCode.toString,
      "outcome" -> "sendEmail request failed",
      "callingService" -> callingService,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

/**************** PasscodeVerificationResponse Events ****************/

  def sendEmailAddressConfirmedEvent(emailAddress: String, passcode: String, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> passcode,
      "passcodeAttempts" -> passcodeDoc.passcodeAttempts.toString,
      "sameEmailAttempts" -> passcodeDoc.emailAttempts.toString,
      "differentEmailAttempts" -> "-",
      "success" -> "true",
      "responseCode" -> responseCode.toString,
      "outcome" -> "Email address confirmed",
      "callingService" -> "-",
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationResponse", details, "HMRC Gateway - Email Verification - verifies the passcode matches with the stored passcode and HMRC email address")
  }

  def sendEmailAddressNotFoundOrExpiredEvent(emailAddress: String, passcode: String, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> passcode,
      "passcodeAttempts" -> "-",
      "sameEmailAttempts" -> "-",
      "differentEmailAttempts" -> "-",
      "success" -> "false",
      "responseCode" -> responseCode.toString,
      "outcome" -> "Email address not found or verification attempt time expired",
      "callingService" -> "-",
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationResponse", details, "HMRC Gateway - Email Verification - verifies the passcode matches with the stored passcode and HMRC email address")
  }

  def sendPasscodeMatchNotFoundOrExpiredEvent(emailAddress: String, passcode: String, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> passcode,
      "passcodeAttempts" -> passcodeDoc.passcodeAttempts.toString,
      "sameEmailAttempts" -> passcodeDoc.emailAttempts.toString,
      "differentEmailAttempts" -> "-",
      "success" -> "false",
      "responseCode" -> responseCode.toString,
      "outcome" -> "Email verification passcode match not found or time expired",
      "callingService" -> "-",
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationResponse", details, "HMRC Gateway - Email Verification - verifies the passcode matches with the stored passcode and HMRC email address")
  }

  def sendVerificationRequestMissingSessionIdEvent(emailAddress: String, passcode: String, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> passcode,
      "passcodeAttempts" -> "-",
      "sameEmailAttempts" -> "-",
      "differentEmailAttempts" -> "-",
      "success" -> "false",
      "responseCode" -> responseCode.toString,
      "outcome" -> "SessionId missing",
      "callingService" -> "-",
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationResponse", details, "HMRC Gateway - Email Verification - verifies the passcode matches with the stored passcode and HMRC email address")
  }

  def sendMaxPasscodeAttemptsExceededEvent(emailAddress: String, passcode: String, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]) = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> passcode,
      "passcodeAttempts" -> passcodeDoc.passcodeAttempts.toString,
      "sameEmailAttempts" -> passcodeDoc.emailAttempts.toString,
      "differentEmailAttempts" -> "-",
      "success" -> "false",
      "responseCode" -> responseCode.toString,
      "outcome" -> "Max permitted passcode verification attempts per session has been exceeded",
      "callingService" -> "-",
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationResponse", details, "HMRC Gateway - Email Verification - verifies the passcode matches with the stored passcode and HMRC email address")
  }

  private def sendEvent(auditType: String, details: Map[String, String], transactionName: String)(implicit request: Request[_]) = {
    val hcDetails = hc.toAuditDetails() ++ details

    val event = DataEvent(auditType   = auditType, tags = hc.toAuditTags(transactionName, request.path), detail = hcDetails, auditSource = "email-verification")
    auditConnector.sendEvent(event).map(_ => ())
  }

}

