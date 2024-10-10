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

import play.api.Logging
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.emailverification.models.SendCodeV2Request
import uk.gov.hmrc.emailverification.services.EmailVerificationV2Service

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TestOnlyEmailVerificationV2Controller @Inject() (
  emailVerificationService: EmailVerificationV2Service,
  controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BaseControllerWithJsonErrorHandling(controllerComponents)
    with Logging {

  def getVerificationCode: Action[SendCodeV2Request] = Action.async(parse.json[SendCodeV2Request]) {
    implicit
    request =>
      val retrieveCodeRequest = request.body
      emailVerificationService
        .getVerificationCode(retrieveCodeRequest)
        .map(vc => Ok(s"""{"code": "${vc.getOrElse("not " + "found")}"}"""))
  }
}
