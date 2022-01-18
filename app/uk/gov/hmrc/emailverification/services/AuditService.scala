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

package uk.gov.hmrc.emailverification.services

import javax.inject.Inject
import play.api.Logging
import play.api.http.HeaderNames
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}
import play.api.mvc.Request
import uk.gov.hmrc.emailverification.models.{PasscodeDoc, VerifyEmailRequest}
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{DataEvent, ExtendedDataEvent}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendHeaderCarrierProvider

import scala.concurrent.{ExecutionContext, Future}

class AuditService @Inject() (
    auditConnector: AuditConnector
)(implicit ec: ExecutionContext) extends BackendHeaderCarrierProvider with Logging {

  def requestContextForLog(implicit request: Request[_]): String = {
    s"(bearerToken:${hc.authorization.getOrElse(Authorization("-")).value}, trueClientIp:${hc.trueClientIp.getOrElse("-")})"
  }

  def sendPasscodeViaEmailEvent(emailAddress: String, passcode: String, serviceName: String, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> passcode,
      "serviceName" -> serviceName,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value,
      "responseCode" -> responseCode.toString
    )

    sendEvent("SendEmailWithPasscode", details, "HMRC Gateway - Email Verification - Send out passcode via Email")
  }

  def sendCheckEmailVerifiedEvent(emailAddress: String, failureReason: Option[String], responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "emailVerified" -> failureReason.fold("true")(_ => "false"),
      "responseCode" -> responseCode.toString,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    ) ++ failureReason.map("verifyFailureReason" -> _).toMap

    sendEvent("CheckEmailVerified", details, "HMRC Gateway - Email Verification - Check Email is verified")
  }

/*********** PasscodeVerificationRequest Events *************/

  def sendEmailPasscodeRequestSuccessfulEvent(emailAddress: String, passcode: String, callingService: String, differentEmailAttempts: Long, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
    val details = Map(
      "emailAddress" -> emailAddress,
      "passcode" -> passcode,
      "passcodeAttempts" -> passcodeDoc.passcodeAttempts.toString,
      "sameEmailAttempts" -> passcodeDoc.emailAttempts.toString,
      "differentEmailAttempts" -> differentEmailAttempts.toString,
      "success" -> "true",
      "responseCode" -> responseCode.toString,
      "outcome" -> "Successfully sent a passcode to the email address requiring verification",
      "callingService" -> callingService,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value
    )
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

  def sendEmailRequestMissingSessionIdEvent(emailAddress: String, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
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
    logger.info(s"[GG-5115] SessionId missing from email passcode request. $requestContextForLog}")
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

  def sendEmailAddressAlreadyVerifiedEvent(emailAddress: String, callingService: String, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
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
    logger.info(s"[GG-5115] Email address already confirmed. $requestContextForLog}")
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

  def sendMaxEmailsExceededEvent(emailAddress: String, callingService: String, differentEmailAttempts: Long, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
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
    logger.info(s"[GG-5115] Max permitted passcode emails per session has been exceeded. $requestContextForLog}")
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

  def sendMaxDifferentEmailsExceededEvent(emailAddress: String, callingService: String, differentEmailAttempts: Long, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
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
    logger.info(s"[GG-5115] Max permitted passcode emails per session has been exceeded. $requestContextForLog}")
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

  def sendPasscodeEmailDeliveryErrorEvent(emailAddress: String, callingService: String, differentEmailAttempts: Long, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
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
    logger.info(s"[GG-5115] sendEmail request failed. $requestContextForLog}")
    sendEvent("PasscodeVerificationRequest", details, "HMRC Gateway - Email Verification - send new passcode to email address")
  }

/**************** PasscodeVerificationResponse Events ****************/

  def sendEmailAddressConfirmedEvent(emailAddress: String, passcode: String, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
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
    logger.info(s"[GG-5074] Email address confirmed $requestContextForLog}")
    sendEvent("PasscodeVerificationResponse", details, "HMRC Gateway - Email Verification - verifies the passcode matches with the stored passcode and HMRC email address")
  }

  def sendEmailAddressNotFoundOrExpiredEvent(emailAddress: String, passcode: String, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
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
    logger.info(s"[GG-5074] Email address not found or verification attempt time expired $requestContextForLog}")
    sendEvent("PasscodeVerificationResponse", details, "HMRC Gateway - Email Verification - verifies the passcode matches with the stored passcode and HMRC email address")
  }

  def sendPasscodeMatchNotFoundOrExpiredEvent(emailAddress: String, passcode: String, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
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
    logger.info(s"[GG-5074] Email verification passcode match not found or time expired $requestContextForLog}")
    sendEvent("PasscodeVerificationResponse", details, "HMRC Gateway - Email Verification - verifies the passcode matches with the stored passcode and HMRC email address")
  }

  def sendVerificationRequestMissingSessionIdEvent(emailAddress: String, passcode: String, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
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
    logger.info(s"[GG-5074] SessionId missing $requestContextForLog}")
    sendEvent("PasscodeVerificationResponse", details, "HMRC Gateway - Email Verification - verifies the passcode matches with the stored passcode and HMRC email address")
  }

  def sendMaxPasscodeAttemptsExceededEvent(emailAddress: String, passcode: String, passcodeDoc: PasscodeDoc, responseCode: Int)(implicit request: Request[_]): Future[Unit] = {
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
    logger.info(s"[GG-5074] Max permitted passcode verification attempts per session has been exceeded $requestContextForLog}")
    sendEvent("PasscodeVerificationResponse", details, "HMRC Gateway - Email Verification - verifies the passcode matches with the stored passcode and HMRC email address")
  }

  //************ JourneyController Events **********************

  def sendVerifyEmailRequestReceivedEvent(verifyEmailRequest: VerifyEmailRequest, responseStatus: Int)(implicit request: Request[_]): Future[Unit] = {
    val details = Map(
      "credId" -> verifyEmailRequest.credId,
      "bearerToken" -> hc.authorization.getOrElse(Authorization("-")).value,
      "origin" -> verifyEmailRequest.origin,
      "continueUrl" -> verifyEmailRequest.continueUrl,
      "deskproServiceName" -> verifyEmailRequest.deskproServiceName.getOrElse("-"),
      "accessibilityStatementUrl" -> verifyEmailRequest.accessibilityStatementUrl,
      "pageTitle" -> verifyEmailRequest.pageTitle.getOrElse("-"),
      "backUrl" -> verifyEmailRequest.backUrl.getOrElse("-"),
      "emailAddress" -> verifyEmailRequest.email.fold("-")(_.address),
      "emailEntryUrl" -> verifyEmailRequest.email.fold("-")(_.enterUrl),
      "lang" -> verifyEmailRequest.lang.fold("-")(_.value),
      "statusCode" -> responseStatus.toString
    )
    logger.info(s"[GG-5646] VerifyEmailRequest received. $requestContextForLog")
    sendEvent("VerifyEmailRequest", details, "HMRC Gateway - Email Verification - a VerifyEmailRequest has been made to verify an email address belongs to the requestor.")
  }

  def sendEmailVerificationOutcomeRequestEvent(credId: String, emailsJsonArray: JsArray, responseStatus: Int)(implicit request: Request[_]): Future[Unit] = {
    val details = Json.obj(
      "credId" -> JsString(credId),
      "bearerToken" -> JsString(hc.authorization.getOrElse(Authorization("-")).value),
      "userAgentString" -> JsString(request.headers.get(HeaderNames.USER_AGENT).getOrElse("-")),
      "emails" -> emailsJsonArray,
      "statusCode" -> JsString(responseStatus.toString)
    )
    logger.info(s"[GG-5646] EmailVerification outcome requested for credId $credId. $requestContextForLog")
    sendEventWithJsonDetails("EmailVerificationOutcomeRequest", details, "HMRC Gateway - Email Verification - a request has been made to check the outcome of an email verification attempt.")
  }

  private def sendEvent(auditType: String, details: Map[String, String], transactionName: String)(implicit request: Request[_]) = {
    val hcDetails = hc.toAuditDetails() ++ details

    val event = DataEvent(auditType   = auditType, tags = hc.toAuditTags(transactionName, request.path), detail = hcDetails, auditSource = "email-verification")
    auditConnector.sendEvent(event).map(_ => ())
  }

  private def sendEventWithJsonDetails(auditType: String, details: JsObject, transactionName: String)(implicit request: Request[_]) = {
    val hcDetails = JsObject(hc.toAuditDetails().map(tuple => tuple._1 -> JsString(tuple._2))) ++ details

    val event = ExtendedDataEvent(auditType   = auditType, tags = hc.toAuditTags(transactionName, request.path), detail = hcDetails.as[JsValue], auditSource = "email-verification")
    auditConnector.sendExtendedEvent(event).map(_ => ())
  }

}

