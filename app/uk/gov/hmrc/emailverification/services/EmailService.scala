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
import uk.gov.hmrc.emailverification.connectors.EmailConnector
import uk.gov.hmrc.emailverification.models.{English, Language, Welsh}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EmailService @Inject() (emailConnector: EmailConnector) {

  def sendPasscodeEmail(emailAddress: String, passcode: String, serviceName: String, lang: Language)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val params = Map(
      "passcode" -> passcode,
      "team_name" -> serviceName
    )

    val templateId = lang match {
      case English => "email_verification_passcode"
      case Welsh   => "email_verification_passcode_welsh"
    }

    emailConnector.sendEmail(emailAddress, templateId, params).map(_ => ())
  }

}
