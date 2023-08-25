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
import play.api.Logging

import java.util.UUID
import javax.inject.Inject
import uk.gov.hmrc.emailverification.models.{CompletedEmail, English, Journey, JourneyData, VerificationStatus, VerifyEmailRequest}
import uk.gov.hmrc.emailverification.repositories.{JourneyRepository, VerificationStatusRepository}
import uk.gov.hmrc.emailverification.utils.JourneyLabelsUtil.{getPageTitleLabel, getTeamNameLabel}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class JourneyService @Inject() (
    emailService:                 EmailService,
    passcodeGenerator:            PasscodeGenerator,
    journeyRepository:            JourneyRepository,
    verificationStatusRepository: VerificationStatusRepository,
    config:                       AppConfig
)(implicit ec: ExecutionContext) extends Logging {

  //return the url on email-verification-frontend for the next step/
  //Only sends email if we have an email address to send to, otherwise will send when user comes back from frontend with
  //an email address
  def initialise(verifyEmailRequest: VerifyEmailRequest)(implicit hc: HeaderCarrier): Future[String] = {

      def createQueryParams(continueUrl: String, origin: String, serviceName: String): String =
        s"continueUrl=$continueUrl" +
          s"&origin=$origin" +
          s"&service=$serviceName"

    val passcode = passcodeGenerator.generate()
    val journeyId = UUID.randomUUID().toString

    val emailAddressAttempts = if (verifyEmailRequest.email.isDefined) 1 else 0

    val journey = Journey(
      journeyId                 = journeyId,
      credId                    = verifyEmailRequest.credId,
      continueUrl               = verifyEmailRequest.continueUrl,
      origin                    = verifyEmailRequest.origin,
      accessibilityStatementUrl = verifyEmailRequest.accessibilityStatementUrl,
      serviceName               = getTeamNameLabel(verifyEmailRequest),
      language                  = verifyEmailRequest.lang.getOrElse(English),
      emailAddress              = verifyEmailRequest.email.map(_.address),
      enterEmailUrl             = verifyEmailRequest.email.map(_.enterUrl),
      backUrl                   = verifyEmailRequest.backUrl,
      pageTitle                 = getPageTitleLabel(verifyEmailRequest),
      passcode                  = passcode,
      emailAddressAttempts      = emailAddressAttempts,
      passcodesSentToEmail      = 0,
      passcodeAttempts          = 0
    )
    for {
      _ <- journeyRepository.initialise(journey)
      _ <- journey.emailAddress.fold(Future.unit)(saveEmailAndSendPasscode(_, journey))
    } yield if (verifyEmailRequest.email.isEmpty) {
      s"/email-verification/journey/$journeyId/email?" +
        createQueryParams(verifyEmailRequest.continueUrl, verifyEmailRequest.origin, journey.serviceName)
    } else {
      s"/email-verification/journey/$journeyId/passcode?" +
        createQueryParams(verifyEmailRequest.continueUrl, verifyEmailRequest.origin, journey.serviceName)
    }
  }

  private def saveEmailAndSendPasscode(email: String, journey: Journey)(implicit hc: HeaderCarrier): Future[Unit] = {
    verificationStatusRepository.initialise(journey.credId, email).flatMap { _ =>
      emailService.sendPasscodeEmail(email, journey.passcode, journey.serviceName, journey.language)
    }
  }

  def checkIfEmailExceedsCount(credId: String, emailAddress: String): Future[Boolean] = {
    emailShouldBeLocked(credId: String, emailAddress: String).flatMap { emailShouldBeLocked =>
      if (emailShouldBeLocked) {
        verificationStatusRepository.lock(credId, emailAddress)
        Future.successful(true)
      } else Future.successful(false)
    }
  }

  private def emailShouldBeLocked(credId: String, emailAddress: String): Future[Boolean] = {
    journeyRepository.findByCredId(credId).flatMap { journeys =>
      val sumOfPasscodesSentToSameEmail = journeys.filter(journey => journey.emailAddress.contains(emailAddress)).map(journey => journey.passcodesSentToEmail).sum + 1
      val includeNewEmail = if (journeys.flatMap(_.emailAddress).contains(emailAddress)) 0 else 1
      val sumOfEmailAttemptsInJourneys = journeys.distinctBy(journey => journey.emailAddress).map(journey => journey.emailAddressAttempts).sum + includeNewEmail
      if (sumOfEmailAttemptsInJourneys > config.maxDifferentEmails || sumOfPasscodesSentToSameEmail > config.maxAttemptsPerEmail) {
        logger.info(s"[GG-6678] either too many emails or too many passcodes to credId: $credId, the passcodes sent to email: $sumOfPasscodesSentToSameEmail, the different emails tried: $sumOfEmailAttemptsInJourneys")
        Future.successful(true)
      } else {
        Future.successful(false)
      }
    }
  }

  def getJourney(journeyId: String): Future[Option[JourneyData]] = {
    journeyRepository.get(journeyId).map(_.map(_.frontendData))
  }

  def submitEmail(journeyId: String, email: String)(implicit hc: HeaderCarrier): Future[EmailUpdateResult] = {
    journeyRepository.submitEmail(journeyId, email).flatMap {
      case Some(journey) => journeyRepository.findByCredId(journey.credId).flatMap{
        journeys =>
          val sumOfEmailAttempts = journeys.map(journey => journey.emailAddressAttempts).sum
          val sumOfPasscodesSentToSameEmail = journeys.filter(journey => journey.emailAddress.contains(email)).map(journey => journey.passcodesSentToEmail).sum
          if (sumOfEmailAttempts > config.maxDifferentEmails || sumOfPasscodesSentToSameEmail > config.maxAttemptsPerEmail) {
            logger.info(s"[GG-6678] journey with journeyId: $journeyId has too many max different emails or too many passcodes sent to same email, passcodes sent to email: $sumOfPasscodesSentToSameEmail, different emails tried: $sumOfEmailAttempts")
            val result = EmailUpdateResult.TooManyAttempts(journey.continueUrl)
            journey.emailAddress match {
              case Some(email) => verificationStatusRepository.lock(journey.credId, email).map(_ => result)
              case None        => Future.successful(result)
            }
          } else saveEmailAndSendPasscode(email, journey).map(_ => EmailUpdateResult.Accepted)
      }
      case None =>
        Future.successful(EmailUpdateResult.JourneyNotFound)
    }
  }

  def resendPasscode(journeyId: String)(implicit hc: HeaderCarrier): Future[ResendPasscodeResult] = {
    journeyRepository.recordPasscodeResent(journeyId).flatMap {
      case Some(journey) if journey.passcodeAttempts >= config.maxPasscodeAttempts =>
        val result = ResendPasscodeResult.TooManyAttemptsInSession(journey.continueUrl)
        journey.emailAddress match {
          case Some(email) => verificationStatusRepository.lock(journey.credId, email).map(_ => result)
          case None        => Future.successful(result)
        }
      case Some(journey) if journey.passcodesSentToEmail >= config.maxAttemptsPerEmail => // [GG-6678] recordPasscodeResent increments but does not return an incremented value
        val result = ResendPasscodeResult.TooManyAttemptsForEmail(journey.frontendData)
        journey.emailAddress match {
          case Some(email) => verificationStatusRepository.lock(journey.credId, email).map(_ => result)
          case None        => Future.successful(result)
        }
      case Some(journey) =>
        journey.emailAddress match {
          case Some(email) =>
            emailService.sendPasscodeEmail(
              email,
              journey.passcode,
              journey.serviceName,
              journey.language
            ).map(_ => ResendPasscodeResult.PasscodeResent)
          case None =>
            Future.successful(ResendPasscodeResult.NoEmailProvided)
        }
      case None =>
        Future.successful(ResendPasscodeResult.JourneyNotFound)
    }
  }

  def validatePasscode(journeyId: String, credId: String, passcode: String): Future[PasscodeValidationResult] = {
    journeyRepository.recordPasscodeAttempt(journeyId).flatMap {
      case Some(journey) if journey.passcodeAttempts >= config.maxPasscodeAttempts =>
        val email = journey.emailAddress.getOrElse(throw new IllegalStateException(s"cannot lock email address for credId $credId as no email address found"))
        verificationStatusRepository.lock(credId, email).map { _ =>
          PasscodeValidationResult.TooManyAttempts(journey.continueUrl)
        }
      case Some(journey) if journey.passcode == passcode =>
        val email = journey.emailAddress.getOrElse(throw new IllegalStateException(s"cannot complete journey $journeyId as there is no email address"))
        verificationStatusRepository.verify(credId, email).map { _ =>
          PasscodeValidationResult.Complete(journey.continueUrl)
        }
      case Some(journey) =>
        Future.successful(PasscodeValidationResult.IncorrectPasscode(journey.frontendData))
      case None =>
        Future.successful(PasscodeValidationResult.JourneyNotFound)
    }
  }

  //returns email addresses that are verified or locked
  def findCompletedEmails(credId: String): Future[Seq[CompletedEmail]] = {
    verificationStatusRepository.retrieve(credId)
      .map(
        _.collect {
          case VerificationStatus(email, verified, locked) if verified | locked =>
            CompletedEmail(email, verified, locked)
        }
      )
  }

  def isLocked(credId: String, emailAddress: Option[String]): Future[Boolean] = {
    emailAddress match {
      case None        => Future.successful(false)
      case Some(email) => verificationStatusRepository.isLocked(credId, email)
    }
  }
}

sealed trait EmailUpdateResult
object EmailUpdateResult {
  case object Accepted extends EmailUpdateResult
  case object JourneyNotFound extends EmailUpdateResult
  case class TooManyAttempts(continueUrl: String) extends EmailUpdateResult
}

sealed trait ResendPasscodeResult
object ResendPasscodeResult {
  case object PasscodeResent extends ResendPasscodeResult
  case object JourneyNotFound extends ResendPasscodeResult
  case object NoEmailProvided extends ResendPasscodeResult
  case class TooManyAttemptsInSession(continueUrl: String) extends ResendPasscodeResult
  case class TooManyAttemptsForEmail(journey: JourneyData) extends ResendPasscodeResult
}

sealed trait PasscodeValidationResult
object PasscodeValidationResult {
  case class Complete(continueUrl: String) extends PasscodeValidationResult
  case class IncorrectPasscode(journey: JourneyData) extends PasscodeValidationResult
  case object JourneyNotFound extends PasscodeValidationResult
  case class TooManyAttempts(continueUrl: String) extends PasscodeValidationResult
}
