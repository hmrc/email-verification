/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util.UUID

import org.mockito.captor.ArgCaptor
import uk.gov.hmrc.emailverification.models.{Email, English, Journey, VerifyEmailRequest}
import uk.gov.hmrc.emailverification.repositories.JourneyRepository
import uk.gov.hmrc.gg.test.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class JourneyServiceSpec extends UnitSpec {

  "initialise" when {
    "given a request with an email address present" should {
      "store the journey and send a passcode to the email address and return email-verification-frontend journey url" in new Setup {

        when(mockPasscodeGenerator.generate()).thenReturn(passcode)

        val captor = ArgCaptor[Journey]
        when(mockJourneyRepository.initialise(captor)).thenReturn(Future.unit)

        when(mockEmailService.sendPasscodeEmail(eqTo(emailAddress), eqTo(passcode), eqTo(origin), eqTo(English))(any, any)).thenReturn(Future.unit)

        val res = await(journeyService.initialise(verifyEmailRequest)(HeaderCarrier(), ec))

        res shouldBe s"/email-verification-frontend/journey/${captor.value.journeyId}?continueUrl=$continueUrl&origin=$origin"
      }
    }

    "given a request with no email address present" should {
      "store the journey without sending an email return email-verification-frontend journey url" in new Setup {

        when(mockPasscodeGenerator.generate()).thenReturn(passcode)

        val captor = ArgCaptor[Journey]
        when(mockJourneyRepository.initialise(captor)).thenReturn(Future.unit)

        val res = await(journeyService.initialise(verifyEmailRequest.copy(email = None))(HeaderCarrier(), ec))

        res shouldBe s"/email-verification-frontend/journey/${captor.value.journeyId}?continueUrl=$continueUrl&origin=$origin"

        verifyZeroInteractions(mockEmailService)
      }
    }

    "an email fails to send" should {
      "return a failed future" in new Setup {

        when(mockPasscodeGenerator.generate()).thenReturn(passcode)
        when(mockJourneyRepository.initialise(any)).thenReturn(Future.unit)

        when(mockEmailService.sendPasscodeEmail(eqTo(emailAddress), eqTo(passcode), eqTo(origin), eqTo(English))(any, any))
          .thenReturn(Future.failed(new Exception("failed")))

        lazy val res = await(journeyService.initialise(verifyEmailRequest)(HeaderCarrier(), ec))

        a[Exception] shouldBe thrownBy(res)
      }
    }

    "db fails to store record" should {
      "return a failed future and not send an email" in new Setup {

        when(mockPasscodeGenerator.generate()).thenReturn(passcode)
        when(mockJourneyRepository.initialise(any)).thenReturn(Future.failed(new Exception("failed")))

        lazy val res = await(journeyService.initialise(verifyEmailRequest)(HeaderCarrier(), ec))

        a[Exception] shouldBe thrownBy(res)

        verifyZeroInteractions(mockEmailService)
      }
    }

  }

  trait Setup extends TestData {
    val mockEmailService = mock[EmailService]
    val mockPasscodeGenerator = mock[PasscodeGenerator]
    val mockJourneyRepository = mock[JourneyRepository]
    val journeyService = new JourneyService(mockEmailService, mockPasscodeGenerator, mockJourneyRepository)
  }

  trait TestData {
    val passcode = "FGTRWX"
    val credId = UUID.randomUUID().toString
    val continueUrl = "/plastic-packaging-tax/start"
    val origin = "ppt"
    val emailAddress = "barrywood@hotmail.com"

    val ec = scala.concurrent.ExecutionContext.global

    val verifyEmailRequest = VerifyEmailRequest(
      credId                    = credId,
      continueUrl               = continueUrl,
      origin                    = origin,
      deskproServiceName        = None,
      accessibilityStatementUrl = "/",
      email                     = Some(Email(emailAddress, "/ppt/email")),
      lang                      = English
    )
  }

}
