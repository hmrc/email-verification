/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.emailverification.models

import play.api.http.HeaderNames
import play.api.libs.json.{Format, Json}
import play.api.mvc.Request
import uk.gov.hmrc.emailverification.services.PasscodeGenerator

sealed trait EmailValidation {
  private val emailValidationRegex =
    """^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]{0,63}+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

  def validateEmail(email: String): Boolean =
    emailValidationRegex.matches(email)
}

case class SendCodeV2Request(email: String)

object SendCodeV2Request extends EmailValidation {
  implicit val sendCodeV2RequestFormat: Format[SendCodeV2Request] = Json.format[SendCodeV2Request]

  implicit class ValidatingSendCodeV2Request(emailSendCodeV2Request: SendCodeV2Request) {
    def isEmailValid: Boolean = validateEmail(emailSendCodeV2Request.email)
  }
}

case class SendCodeResult(status: String, message: Option[String]) {
  def isSent: Boolean = SendCodeResult.code.CODE_SENT == status
}

object SendCodeResult {
  implicit val emailSendCodeStatusFormat: Format[SendCodeResult] = Json.format[SendCodeResult]

  def codeSent(message: Option[String] = Some("Email containing verification code has been sent")): SendCodeResult =
    SendCodeResult(code.CODE_SENT, message)
  def codeNotSent(message: String): SendCodeResult = SendCodeResult(code.CODE_NOT_SENT, Some(message))

  private object code {
    val CODE_NOT_SENT: String = "CODE_NOT_SENT"
    val CODE_SENT: String = "CODE_SENT"
  }
}

case class VerifyCodeV2Request(email: String, verificationCode: String)

object VerifyCodeV2Request extends EmailValidation {
  implicit val verifyCodeV2RequestFormat: Format[VerifyCodeV2Request] = Json.format[VerifyCodeV2Request]

  implicit class ValidatingVerifyCodeV2Request(emailVerifyCodeV2Request: VerifyCodeV2Request) {
    def isEmailValid: Boolean = validateEmail(emailVerifyCodeV2Request.email)
    def isVerificationCodeValid: Boolean = PasscodeGenerator.validate(emailVerifyCodeV2Request.verificationCode)
  }
}

case class VerifyCodeResult(status: String, message: Option[String]) {
  def isVerified: Boolean = VerifyCodeResult.code.CODE_VERIFIED == status
  def codeNotFound: Boolean = VerifyCodeResult.code.CODE_NOT_FOUND == status
  def codeNotValid: Boolean = VerifyCodeResult.code.CODE_NOT_VALIDATED == status
}

object VerifyCodeResult {
  implicit val verifyCodeResultFormat: Format[VerifyCodeResult] = Json.format[VerifyCodeResult]

  def codeVerified(message: Option[String] = Some("The verification code for the email verified successfully")): VerifyCodeResult = VerifyCodeResult(code.CODE_VERIFIED, message)
  def codeNotValid(message: Option[String]): VerifyCodeResult = VerifyCodeResult(code.CODE_NOT_VALIDATED, message)
  def codeNotVerified(message: String): VerifyCodeResult = VerifyCodeResult(code.CODE_NOT_VERIFIED, Some(message))
  def codeNotFound(message: String): VerifyCodeResult = VerifyCodeResult(code.CODE_NOT_FOUND, Some(message))

  private object code {
    val CODE_VERIFIED: String = "CODE_VERIFIED"
    val CODE_NOT_VALIDATED: String = "CODE_NOT_VALIDATED"
    val CODE_NOT_VERIFIED: String = "CODE_NOT_VERIFIED"
    val CODE_NOT_FOUND: String = "CODE_NOT_FOUND"
  }
}

case class UserAgent(unwrap: Option[String])
object UserAgent {
  def apply(request: Request[_]): UserAgent = UserAgent(request.headers.get(HeaderNames.USER_AGENT))
}
