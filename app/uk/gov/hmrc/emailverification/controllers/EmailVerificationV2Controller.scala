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

package uk.gov.hmrc.emailverification.controllers

import config.AppConfig
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.emailverification.models.{SendCodeResult, SendCodeV2Request, UserAgent, VerifyCodeResult, VerifyCodeV2Request}
import uk.gov.hmrc.emailverification.services.EmailVerificationV2Service

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class EmailVerificationV2Controller @Inject() (
  emailVerificationService: EmailVerificationV2Service,
  controllerComponents: ControllerComponents,
  override val config: AppConfig
)(implicit ec: ExecutionContext)
    extends BaseControllerWithJsonErrorHandling(controllerComponents)
    with AccessChecker
    with Logging {

  // 1. Validate the email - check that it looks correct-ish
  // 2. Generate a verification code
  // 3. Send the verification code email to provided email address
  // 4. If successful, persist the verification code
  // 5. Respond with an OK if all went well else ???
  def sendCode(): Action[SendCodeV2Request] = accessCheckedAction(parse.json[SendCodeV2Request]) { implicit request =>
    import uk.gov.hmrc.emailverification.models.SendCodeResult._
    implicit val userAgent: UserAgent = UserAgent(request)

    val sendCodeRequest: SendCodeV2Request = request.body
    emailVerificationService.doSendCode(sendCodeRequest).map {
      case sendCodeResult: SendCodeResult if sendCodeResult.isSent => Ok(Json.toJson(sendCodeResult))
      case sendCodeResult                                          => BadRequest(Json.toJson(sendCodeResult))
    }
  }

  def verifyCode(): Action[VerifyCodeV2Request] = accessCheckedAction(parse.json[VerifyCodeV2Request]) { implicit request =>
    import uk.gov.hmrc.emailverification.models.VerifyCodeResult._
    implicit val userAgent: UserAgent = UserAgent(request)

    val verifyCodeRequest = request.body
    emailVerificationService.doVerifyCode(verifyCodeRequest).map {
      case verifyResult: VerifyCodeResult if verifyResult.isVerified   => Ok(Json.toJson(verifyResult))
      case verifyResult: VerifyCodeResult if verifyResult.codeNotValid => BadRequest(Json.toJson(verifyResult))
      case verifyResult: VerifyCodeResult                              => NotFound(Json.toJson(verifyResult))
    }
  }
}
