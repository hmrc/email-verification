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

import javax.inject.Inject
import uk.gov.hmrc.emailverification.models.{Journey, VerifyEmailRequest}
import uk.gov.hmrc.emailverification.repositories.JourneyRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class JourneyService @Inject() (
    emailService:      EmailService,
    passcodeGenerator: PasscodeGenerator,
    journeyRepository: JourneyRepository
) {

  //return the url on email-verification-frontend for the next step/
  //Only sends email if we have an email address to send to, otherwise will send when user comes back from frontend with
  //an email address
  def initialise(verifyEmailRequest: VerifyEmailRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    val passcode = passcodeGenerator.generate()
    val journeyId = UUID.randomUUID().toString

    val journey = Journey(
      journeyId                 = journeyId,
      credId                    = verifyEmailRequest.credId,
      continueUrl               = verifyEmailRequest.continueUrl,
      origin                    = verifyEmailRequest.origin,
      accessibilityStatementUrl = verifyEmailRequest.accessibilityStatementUrl,
      email                     = verifyEmailRequest.email,
      passcode                  = passcode,
    )

    for {
      _ <- journeyRepository.initialise(journey)
      _ <- verifyEmailRequest.email.fold(Future.unit){ email =>
        emailService.sendPasscodeEmail(
          email.address,
          passcode,
          verifyEmailRequest.deskproServiceName.getOrElse(verifyEmailRequest.origin),
          verifyEmailRequest.lang
        )
      }
    } yield s"/email-verification-frontend/journey/$journeyId?" +
      s"continueUrl=${verifyEmailRequest.continueUrl}" +
      s"&origin=${verifyEmailRequest.origin}"

  }

}
