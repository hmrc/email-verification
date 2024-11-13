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

package uk.gov.hmrc.emailverification.services

import play.api.Logging
import uk.gov.hmrc.emailverification.models.{SendCodeResult, VerifyCodeResult}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AuditV2Service @Inject() (
  auditConnector: AuditConnector
)(implicit ec: ExecutionContext)
    extends Logging {

  def sendVerificationCode(emailAddress: String, verificationCode: String, serviceName: String, responseCode: SendCodeResult)(implicit hc: HeaderCarrier): Future[Unit] = {
    val details = Map[String, String](
      "email"            -> emailAddress,
      "verificationCode" -> verificationCode,
      "serviceName"      -> serviceName,
      "bearerToken"      -> hc.authorization.getOrElse(Authorization("-")).value,
      "isSuccessful"     -> responseCode.isSent.toString,
      "responseCode"     -> responseCode.status
    )
    sendEvent("EmailVerificationCodeSent", details, "/email-verification/v2/send-code")
  }

  def verifyVerificationCode(emailAddress: String, verificationCode: String, serviceName: String, verified: VerifyCodeResult)(implicit hc: HeaderCarrier): Future[Unit] = {
    val details = Map[String, String](
      "email"            -> emailAddress,
      "verificationCode" -> verificationCode,
      "serviceName"      -> serviceName,
      "bearerToken"      -> hc.authorization.getOrElse(Authorization("-")).value,
      "isSuccessful"     -> verified.isVerified.toString,
      "responseCode"     -> verified.status
    )

    sendEvent("EmailVerificationCodeVerified", details, "/email-verification/v2/verify-code")
  }

  private def sendEvent(auditType: String, details: Map[String, String], path: String)(implicit hc: HeaderCarrier) = {
    logger.info(s"""sendEvent(auditType: "$auditType", details: "${details.mkString(",")}", transactionName: "$path")""")
    val hcDetails = hc.toAuditDetails() ++ details

    val event = DataEvent(auditType = auditType, tags = hc.toAuditTags(path), detail = hcDetails, auditSource = "email-verification")
    auditConnector.sendEvent(event).map(_ => ())
  }
}
