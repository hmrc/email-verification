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

import uk.gov.hmrc.emailverification.connectors.EmailConnector
import uk.gov.hmrc.emailverification.models.{English, Welsh}
import uk.gov.hmrc.gg.test.UnitSpec
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class EmailServiceSpec extends UnitSpec {

  trait Setup extends TestData {
    val mockEmailConnector = mock[EmailConnector]
    val emailService = new EmailService(mockEmailConnector)
  }

  trait TestData {
    val emailAddress = "barrywood@hotmail.com"
    val passcode = "RTYGBV"
    val pageTitle = "Page Title"
    val serviceName = "ppt"

    val englishEmailTemplateId = "email_verification_passcode"
    val welshEmailTemplateId = "email_verification_passcode_welsh"

    val hc = HeaderCarrier()
    val ec = scala.concurrent.ExecutionContext.global
  }

  "sendPasscodeEmail" when {
    "given an English lang param" should {
      "add passcode and serviceName to template parameters and use the english template" in new Setup {

        val templateParameters = Map("passcode" -> passcode, "team_name" -> serviceName)
        when(mockEmailConnector.sendEmail(eqTo(emailAddress), eqTo(englishEmailTemplateId), eqTo(templateParameters))(any, any)).thenReturn(Future.successful(HttpResponse(200, "")))

        emailService.sendPasscodeEmail(emailAddress, passcode, serviceName, English)(hc, ec)
      }
    }

    "given an Welsh lang param" should {
      "add passcode and serviceName to template parameters and use the english template" in new Setup {

        val templateParameters = Map("passcode" -> passcode, "team_name" -> serviceName)
        when(mockEmailConnector.sendEmail(eqTo(emailAddress), eqTo(welshEmailTemplateId), eqTo(templateParameters))(any, any)).thenReturn(Future.successful(HttpResponse(200, "")))

        emailService.sendPasscodeEmail(emailAddress, passcode, serviceName, Welsh)(hc, ec)
      }
    }
  }
}
