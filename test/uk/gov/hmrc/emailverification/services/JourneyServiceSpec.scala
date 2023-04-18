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

import config.AppConfig

import java.util.UUID
import org.mockito.captor.{ArgCaptor, Captor}
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.{JourneyRepository, VerificationStatusRepository}
import uk.gov.hmrc.gg.test.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class JourneyServiceSpec extends UnitSpec {

  "initialise" when {
    "given a request with an email address present and no Deskpro service name" should {
      "store the journey and send a passcode to the email address and return email-verification-frontend journey url" in new Setup {
        when(mockPasscodeGenerator.generate()).thenReturn(passcode)
        when(mockVerificationStatusRepository.initialise(eqTo(credId), eqTo(emailAddress))).thenReturn(Future.unit)

        val captor = ArgCaptor[Journey]
        when(mockJourneyRepository.initialise(captor)).thenReturn(Future.unit)
        when(mockEmailService.sendPasscodeEmail(eqTo(emailAddress), eqTo(passcode), eqTo(origin), eqTo(English))(any, any)).thenReturn(Future.unit)

        val res = await(journeyService.initialise(verifyEmailRequest)(HeaderCarrier()))

        res shouldBe s"/email-verification/journey/${captor.value.journeyId}/passcode?continueUrl=$continueUrl&origin=$origin&service=$origin"
      }
    }

    "given a request with an email address present and a Deskpro service name" should {
      "store the journey and send a passcode to the email address and return email-verification-frontend journey url" in new Setup {

        val serviceName = "My Cool Service"

        when(mockPasscodeGenerator.generate()).thenReturn(passcode)
        when(mockVerificationStatusRepository.initialise(eqTo(credId), eqTo(emailAddress))).thenReturn(Future.unit)

        val captor: Captor[Journey] = ArgCaptor[Journey]
        when(mockJourneyRepository.initialise(captor)).thenReturn(Future.unit)

        when(mockEmailService.sendPasscodeEmail(eqTo(emailAddress), eqTo(passcode), eqTo(serviceName), eqTo(English))(any, any)).thenReturn(Future.unit)

        val res: String = await(journeyService.initialise(verifyEmailRequest.copy(deskproServiceName = Some(serviceName)))(HeaderCarrier()))

        res shouldBe s"/email-verification/journey/${captor.value.journeyId}/passcode?continueUrl=$continueUrl&origin=$origin&service=$serviceName"
      }
    }

    "given a request with no email address present and no Deskpro service name" should {
      "store the journey without sending an email return email-verification-frontend journey url" in new Setup {

        val serviceName = "My Cool Service"

        when(mockPasscodeGenerator.generate()).thenReturn(passcode)

        val captor: Captor[Journey] = ArgCaptor[Journey]
        when(mockJourneyRepository.initialise(captor)).thenReturn(Future.unit)

        val res: String = await(journeyService.initialise(verifyEmailRequest.copy(email = None).copy(deskproServiceName = Some(serviceName)))(HeaderCarrier()))

        res shouldBe s"/email-verification/journey/${captor.value.journeyId}/email?continueUrl=$continueUrl&origin=$origin&service=$serviceName"

        verifyZeroInteractions(mockEmailService)
      }
    }

    "given a request with no email address present and a Deskpro service name" should {
      "store the journey without sending an email return email-verification-frontend journey url" in new Setup {
        when(mockPasscodeGenerator.generate()).thenReturn(passcode)

        val captor = ArgCaptor[Journey]
        when(mockJourneyRepository.initialise(captor)).thenReturn(Future.unit)

        val res = await(journeyService.initialise(verifyEmailRequest.copy(email = None))(HeaderCarrier()))

        res shouldBe s"/email-verification/journey/${captor.value.journeyId}/email?continueUrl=$continueUrl&origin=$origin&service=$origin"

        verifyZeroInteractions(mockEmailService)

      }
    }

    "an email fails to send" should {
      "return a failed future" in new Setup {

        when(mockPasscodeGenerator.generate()).thenReturn(passcode)
        when(mockVerificationStatusRepository.initialise(eqTo(credId), eqTo(emailAddress))).thenReturn(Future.unit)
        when(mockJourneyRepository.initialise(any)).thenReturn(Future.unit)
        when(mockEmailService.sendPasscodeEmail(eqTo(emailAddress), eqTo(passcode), eqTo(origin), eqTo(English))(any, any))
          .thenReturn(Future.failed(new Exception("failed")))

        lazy val res = await(journeyService.initialise(verifyEmailRequest)(HeaderCarrier()))

        a[Exception] shouldBe thrownBy(res)
      }
    }

    "db fails to store record" should {
      "return a failed future and not send an email" in new Setup {

        when(mockPasscodeGenerator.generate()).thenReturn(passcode)
        when(mockJourneyRepository.initialise(any)).thenReturn(Future.failed(new Exception("failed")))

        lazy val res = await(journeyService.initialise(verifyEmailRequest)(HeaderCarrier()))

        a[Exception] shouldBe thrownBy(res)

        verifyZeroInteractions(mockEmailService)
      }
    }
  }

  "submitEmail" when {
    "the user has tried too many emails on one journey" should {
      "return TooManyAttempts and the continue URL" in new Setup {
        val journeyId = UUID.randomUUID().toString
        val email = "aaa@bbb.ccc"

        when(mockJourneyRepository.submitEmail(journeyId, email)).thenReturn(Future.successful(Some(Journey(
          journeyId                 = journeyId,
          credId                    = "credId",
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = "My Cool Service",
          language                  = English,
          emailAddress              = Some(email),
          enterEmailUrl             = None,
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = "code",
          emailAddressAttempts      = 2,
          passcodesSentToEmail      = 0,
          passcodeAttempts          = 0
        ))))

        when(mockJourneyRepository.findByCredId("credId")).thenReturn(Future.successful(Seq(Journey(
          journeyId                 = journeyId,
          credId                    = "credId",
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = "My Cool Service",
          language                  = English,
          emailAddress              = Some(email),
          enterEmailUrl             = None,
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = "code",
          emailAddressAttempts      = 2,
          passcodesSentToEmail      = 0,
          passcodeAttempts          = 0
        ))))

        when(mockAppConfig.maxDifferentEmails).thenReturn(1)

        when(mockVerificationStatusRepository.lock(eqTo("credId"), eqTo(email))).thenReturn(Future.unit)

        val result = await(journeyService.submitEmail(journeyId, email)(HeaderCarrier()))
        result shouldBe EmailUpdateResult.TooManyAttempts(continueUrl)
      }
    }

    "the user has tried too many emails over all journeys for the credId" should {
      "return TooManyAttempts and the continue URL" in new Setup {
        val journeyId = UUID.randomUUID().toString
        val someOtherJourneyId = UUID.randomUUID().toString
        val email = "aaa@bbb.ccc"
        val someOtherEmail = "bbb@ccc.ddd"

        when(mockJourneyRepository.submitEmail(journeyId, email)).thenReturn(Future.successful(Some(Journey(
          journeyId                 = journeyId,
          credId                    = "credId",
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = "My Cool Service",
          language                  = English,
          emailAddress              = Some(email),
          enterEmailUrl             = None,
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = "code",
          emailAddressAttempts      = 1,
          passcodesSentToEmail      = 0,
          passcodeAttempts          = 0
        ))))

        when(mockJourneyRepository.findByCredId("credId")).thenReturn(Future.successful(Seq(
          Journey(
            journeyId                 = journeyId,
            credId                    = "credId",
            continueUrl               = continueUrl,
            origin                    = origin,
            accessibilityStatementUrl = "/accessibility",
            serviceName               = "My Cool Service",
            language                  = English,
            emailAddress              = Some(email),
            enterEmailUrl             = None,
            backUrl                   = None,
            pageTitle                 = None,
            passcode                  = "code",
            emailAddressAttempts      = 1,
            passcodesSentToEmail      = 0,
            passcodeAttempts          = 0
          ),
          Journey(
            journeyId                 = someOtherJourneyId,
            credId                    = "credId",
            continueUrl               = continueUrl,
            origin                    = origin,
            accessibilityStatementUrl = "/accessibility",
            serviceName               = "My Cool Service",
            language                  = English,
            emailAddress              = Some(someOtherEmail),
            enterEmailUrl             = None,
            backUrl                   = None,
            pageTitle                 = None,
            passcode                  = "code",
            emailAddressAttempts      = 1,
            passcodesSentToEmail      = 0,
            passcodeAttempts          = 0
          )
        )))

        when(mockAppConfig.maxDifferentEmails).thenReturn(1)

        when(mockVerificationStatusRepository.lock(eqTo("credId"), eqTo(email))).thenReturn(Future.unit)

        val result = await(journeyService.submitEmail(journeyId, email)(HeaderCarrier()))
        result shouldBe EmailUpdateResult.TooManyAttempts(continueUrl)
      }
    }

    "the journeyId is not valid" should {
      "return JourneyNotFound" in new Setup {
        val journeyId = UUID.randomUUID().toString
        val email = "aaa@bbb.ccc"

        when(mockJourneyRepository.submitEmail(journeyId, email)).thenReturn(Future.successful(None))

        val result = await(journeyService.submitEmail(journeyId, email)(HeaderCarrier()))
        result shouldBe EmailUpdateResult.JourneyNotFound
      }
    }

    "the email is accepted" should {
      "save the email as unverified, send a passcode email, and return Accepted" in new Setup {
        val journeyId = UUID.randomUUID().toString
        val email = "aaa@bbb.ccc"
        val serviceName = "My Cool Service"

        when(mockJourneyRepository.submitEmail(journeyId, email)).thenReturn(Future.successful(Some(Journey(
          journeyId                 = journeyId,
          credId                    = credId,
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = serviceName,
          language                  = English,
          emailAddress              = Some(email),
          enterEmailUrl             = None,
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = passcode,
          emailAddressAttempts      = 1,
          passcodesSentToEmail      = 0,
          passcodeAttempts          = 0
        ))))

        when(mockJourneyRepository.findByCredId(credId)).thenReturn(Future.successful(Seq(Journey(
          journeyId                 = journeyId,
          credId                    = credId,
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = serviceName,
          language                  = English,
          emailAddress              = Some(email),
          enterEmailUrl             = None,
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = passcode,
          emailAddressAttempts      = 1,
          passcodesSentToEmail      = 0,
          passcodeAttempts          = 0
        ))))

        when(mockAppConfig.maxDifferentEmails).thenReturn(10)
        when(mockAppConfig.maxAttemptsPerEmail).thenReturn(5)
        when(mockVerificationStatusRepository.initialise(eqTo(credId), eqTo(email))).thenReturn(Future.unit)
        when(mockEmailService.sendPasscodeEmail(eqTo(email), eqTo(passcode), eqTo(serviceName), eqTo(English))(any, any)).thenReturn(Future.unit)

        val result = await(journeyService.submitEmail(journeyId, email)(HeaderCarrier()))
        result shouldBe EmailUpdateResult.Accepted
      }
    }
  }

  "resendPasscode" when {
    "the journey ID is not valid" should {
      "return JourneyNotFound" in new Setup {
        val journeyId = UUID.randomUUID().toString

        when(mockJourneyRepository.recordPasscodeResent(journeyId)).thenReturn(Future.successful(None))

        val result = await(journeyService.resendPasscode(journeyId)(HeaderCarrier()))
        result shouldBe ResendPasscodeResult.JourneyNotFound
      }
    }

    "the journey does not contain an email" should {
      "return NoEmailProvided" in new Setup {
        val journeyId = UUID.randomUUID().toString
        val serviceName = "My Cool Service"

        when(mockJourneyRepository.recordPasscodeResent(journeyId)).thenReturn(Future.successful(Some(Journey(
          journeyId                 = journeyId,
          credId                    = "credId",
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = serviceName,
          language                  = English,
          emailAddress              = None,
          enterEmailUrl             = None,
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = passcode,
          emailAddressAttempts      = 1,
          passcodesSentToEmail      = 0,
          passcodeAttempts          = 0
        ))))
        when(mockAppConfig.maxAttemptsPerEmail).thenReturn(100)
        when(mockAppConfig.maxPasscodeAttempts).thenReturn(100)

        val result = await(journeyService.resendPasscode(journeyId)(HeaderCarrier()))
        result shouldBe ResendPasscodeResult.NoEmailProvided
      }
    }

    "too many passcodes have been sent to the provided email" should {
      "return TooManyAttemptsForEmail and the enter email URL if provided" in new Setup {
        val journeyId = UUID.randomUUID().toString
        val serviceName = "My Cool Service"
        val email = "aaa@bbb.ccc"

        when(mockJourneyRepository.recordPasscodeResent(journeyId)).thenReturn(Future.successful(Some(Journey(
          journeyId                 = journeyId,
          credId                    = "credId",
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = serviceName,
          language                  = English,
          emailAddress              = Some(email),
          enterEmailUrl             = Some("/enterEmail"),
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = passcode,
          emailAddressAttempts      = 1,
          passcodesSentToEmail      = 2,
          passcodeAttempts          = 2
        ))))
        when(mockAppConfig.maxAttemptsPerEmail).thenReturn(1)
        when(mockAppConfig.maxPasscodeAttempts).thenReturn(100)
        when(mockVerificationStatusRepository.lock(eqTo("credId"), eqTo(email))).thenReturn(Future.unit)

        val result = await(journeyService.resendPasscode(journeyId)(HeaderCarrier()))
        result shouldBe ResendPasscodeResult.TooManyAttemptsForEmail(JourneyData("/accessibility", serviceName, Some("/enterEmail"), None, None, Some(email)))
      }
    }

    "too many passcodes have been sent in the session" should {
      "return TooManyAttemptsInSession and the continue URL" in new Setup {
        val journeyId = UUID.randomUUID().toString
        val serviceName = "My Cool Service"
        val email = "aaa@bbb.ccc"

        when(mockJourneyRepository.recordPasscodeResent(journeyId)).thenReturn(Future.successful(Some(Journey(
          journeyId                 = journeyId,
          credId                    = "credId",
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = serviceName,
          language                  = English,
          emailAddress              = Some(email),
          enterEmailUrl             = Some("/enterEmail"),
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = passcode,
          emailAddressAttempts      = 1,
          passcodesSentToEmail      = 1,
          passcodeAttempts          = 2
        ))))
        when(mockAppConfig.maxPasscodeAttempts).thenReturn(1)
        when(mockVerificationStatusRepository.lock(eqTo("credId"), eqTo(email))).thenReturn(Future.unit)

        val result = await(journeyService.resendPasscode(journeyId)(HeaderCarrier()))
        result shouldBe ResendPasscodeResult.TooManyAttemptsInSession(continueUrl)
      }
    }

    "the passcode can be re-sent" should {
      "send the email again and return PasscodeResent" in new Setup {
        val journeyId = UUID.randomUUID().toString
        val serviceName = "My Cool Service"
        val email = "aaa@bbb.ccc"

        when(mockJourneyRepository.recordPasscodeResent(journeyId)).thenReturn(Future.successful(Some(Journey(
          journeyId                 = journeyId,
          credId                    = "credId",
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = serviceName,
          language                  = English,
          emailAddress              = Some(email),
          enterEmailUrl             = Some("/enterEmail"),
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = passcode,
          emailAddressAttempts      = 1,
          passcodesSentToEmail      = 1,
          passcodeAttempts          = 1
        ))))
        when(mockAppConfig.maxAttemptsPerEmail).thenReturn(1)
        when(mockAppConfig.maxPasscodeAttempts).thenReturn(100)
        when(mockEmailService.sendPasscodeEmail(eqTo(email), eqTo(passcode), eqTo(serviceName), eqTo(English))(any, any)).thenReturn(Future.unit)

        val result = await(journeyService.resendPasscode(journeyId)(HeaderCarrier()))
        result shouldBe ResendPasscodeResult.PasscodeResent
      }
    }
  }

  "validatePasscode" when {
    "the journey ID is not valid" should {
      "return JourneyNotFound" in new Setup {
        val journeyId = UUID.randomUUID().toString

        when(mockJourneyRepository.recordPasscodeAttempt(journeyId)).thenReturn(Future.successful(None))

        val result = await(journeyService.validatePasscode(journeyId, credId, passcode))
        result shouldBe PasscodeValidationResult.JourneyNotFound
      }
    }

    "too many attempts have been made" should {
      "return TooManyAttempts and the continue URL" in new Setup {
        val journeyId = UUID.randomUUID().toString

        when(mockJourneyRepository.recordPasscodeAttempt(journeyId)).thenReturn(Future.successful(Some(Journey(
          journeyId                 = journeyId,
          credId                    = credId,
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = "some service",
          language                  = English,
          emailAddress              = Some("aa@bb.cc"),
          enterEmailUrl             = None,
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = passcode,
          emailAddressAttempts      = 1,
          passcodesSentToEmail      = 0,
          passcodeAttempts          = 2
        ))))
        when(mockAppConfig.maxPasscodeAttempts).thenReturn(1)

        when(mockVerificationStatusRepository.lock(any, any)).thenReturn(Future.unit)

        val result = await(journeyService.validatePasscode(journeyId, credId, passcode))
        result shouldBe PasscodeValidationResult.TooManyAttempts(continueUrl)

        verify(mockVerificationStatusRepository).lock(credId, "aa@bb.cc")
      }
    }

    "the passcode is incorrect" should {
      "return IncorrectPasscode and the enter email URL" in new Setup {
        val journeyId = UUID.randomUUID().toString
        val enterEmailUrl = Some("/enterEmail")

        when(mockJourneyRepository.recordPasscodeAttempt(journeyId)).thenReturn(Future.successful(Some(Journey(
          journeyId                 = journeyId,
          credId                    = credId,
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = "some service",
          language                  = English,
          emailAddress              = Some("aa@bb.cc"),
          enterEmailUrl             = enterEmailUrl,
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = passcode,
          emailAddressAttempts      = 1,
          passcodesSentToEmail      = 0,
          passcodeAttempts          = 0
        ))))
        when(mockAppConfig.maxPasscodeAttempts).thenReturn(100)

        val result = await(journeyService.validatePasscode(journeyId, credId, passcode.reverse))
        result shouldBe PasscodeValidationResult.IncorrectPasscode(JourneyData("/accessibility", "some service", enterEmailUrl, None, None, Some("aa@bb.cc")))
      }
    }

    "the passcode is correct" should {
      "return Complete and the continue URL" in new Setup {
        val journeyId = UUID.randomUUID().toString
        val enterEmailUrl = Some("/enterEmail")

        when(mockJourneyRepository.recordPasscodeAttempt(journeyId)).thenReturn(Future.successful(Some(Journey(
          journeyId                 = journeyId,
          credId                    = credId,
          continueUrl               = continueUrl,
          origin                    = origin,
          accessibilityStatementUrl = "/accessibility",
          serviceName               = "some service",
          language                  = English,
          emailAddress              = Some("aa@bb.cc"),
          enterEmailUrl             = enterEmailUrl,
          backUrl                   = None,
          pageTitle                 = None,
          passcode                  = passcode,
          emailAddressAttempts      = 1,
          passcodesSentToEmail      = 0,
          passcodeAttempts          = 0
        ))))
        when(mockAppConfig.maxPasscodeAttempts).thenReturn(100)
        when(mockVerificationStatusRepository.verify(eqTo(credId), eqTo("aa@bb.cc"))).thenReturn(Future.unit)

        val result = await(journeyService.validatePasscode(journeyId, credId, passcode))
        result shouldBe PasscodeValidationResult.Complete(continueUrl)
      }
    }
  }

  "findCompletedEmails" when {
    "mongo retrieves a list of unverified and unlocked emails" should {
      "return an empty list" in new Setup {
        val emails = List(
          VerificationStatus("email1", verified = false, locked = false),
          VerificationStatus("email2", verified = false, locked = false),
          VerificationStatus("email3", verified = false, locked = false),
        )

        when(mockVerificationStatusRepository.retrieve(eqTo(credId))).thenReturn(Future.successful(emails))

        val res = await(journeyService.findCompletedEmails(credId))

        res shouldBe List.empty
      }
    }

    "mongo retrieves a list of a variety emails" should {
      "return a list of the emails that are locked or verified" in new Setup {
        val emails = List(
          VerificationStatus("email1", verified = true, locked = false),
          VerificationStatus("email2", verified = false, locked = false),
          VerificationStatus("email3", verified = false, locked = true),
        )

        when(mockVerificationStatusRepository.retrieve(eqTo(credId))).thenReturn(Future.successful(emails))

        val res = await(journeyService.findCompletedEmails(credId))

        res shouldBe List(
          CompletedEmail("email1", verified = true, locked = false),
          CompletedEmail("email3", verified = false, locked = true),
        )
      }
    }
  }

  trait Setup extends TestData {
    val mockEmailService = mock[EmailService]
    val mockPasscodeGenerator = mock[PasscodeGenerator]
    val mockJourneyRepository = mock[JourneyRepository]
    val mockVerificationStatusRepository = mock[VerificationStatusRepository]
    val mockAppConfig = mock[AppConfig]

    val journeyService = new JourneyService(
      mockEmailService,
      mockPasscodeGenerator,
      mockJourneyRepository,
      mockVerificationStatusRepository,
      mockAppConfig
    )(ExecutionContext.global)
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
      lang                      = Some(English),
      backUrl                   = None,
      pageTitle                 = None,
    )

    val testJourney = Journey(
      journeyId                 = "journeyId",
      credId                    = credId,
      continueUrl               = continueUrl,
      origin                    = "origin",
      accessibilityStatementUrl = "accessibilityStatementUrl",
      serviceName               = "serviceName",
      language                  = English,
      emailAddress              = Some(emailAddress),
      enterEmailUrl             = None,
      backUrl                   = None,
      pageTitle                 = None,
      passcode                  = passcode,
      emailAddressAttempts      = 0,
      passcodesSentToEmail      = 0,
      passcodeAttempts          = 0
    )
  }

}
