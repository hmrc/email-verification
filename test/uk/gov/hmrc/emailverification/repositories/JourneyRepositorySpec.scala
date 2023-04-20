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

package uk.gov.hmrc.emailverification.repositories

import config.AppConfig
import uk.gov.hmrc.emailverification.models.{English, Journey}

import java.util.UUID

class JourneyRepositorySpec extends RepositoryBaseSpec {

  val config: AppConfig = mock[AppConfig]

  val repository = new JourneyMongoRepository(mongoComponent)

  "recordPasscodeAttempt" should {
    "increment the passcode, but not return the incremented passcode value but the old passcode value" in {
      val journeyId = UUID.randomUUID().toString

      val testJourney = Journey(
        journeyId,
        "credId",
        "/continueUrl",
        "origin",
        "/accessibility",
        "serviceName",
        English,
        Some("aa@bb.cc"),
        Some("/enterEmailUrl"),
        Some("/back"),
        Some("title"),
        "passcode",
        1,
        passcodesSentToEmail = 1,
        passcodeAttempts     = 0
      )

      whenReady(repository.initialise(testJourney)){ value =>
        whenReady(repository.recordPasscodeAttempt(journeyId)) { journey =>
          journey shouldBe Some(testJourney)
          whenReady(repository.get(journeyId)) { journey =>
            journey shouldBe Some(testJourney.copy(passcodeAttempts = 1))
          }
        }
      }
    }
  }
  "submitEmail" should {
    "increase the increment on submission of the same email" in {
      val journeyId = UUID.randomUUID().toString
      val email = "aaa@bbb.ccc"

      val testJourney = Journey(
        journeyId,
        "credId",
        "/continueUrl",
        "origin",
        "/accessibility",
        "serviceName",
        English,
        Some(email),
        Some("/enterEmailUrl"),
        Some("/back"),
        Some("title"),
        "passcode",
        1,
        passcodesSentToEmail = 1,
        passcodeAttempts     = 0
      )

      whenReady(repository.initialise(testJourney)){ value =>
        whenReady(repository.submitEmail(journeyId, email)) { journey =>
          journey shouldBe Some(testJourney.copy(passcodesSentToEmail = 2))
        }
      }
    }
    "reset the passcodesSentToEmail value and increment the maxDifferentEmailsValue" in {
      val journeyId = UUID.randomUUID().toString
      val email = "aaa@bbb.ccc"
      val someOtherEmail = "bbb@ccc.ddd"

      val testJourney = Journey(
        journeyId,
        "credId",
        "/continueUrl",
        "origin",
        "/accessibility",
        "serviceName",
        English,
        Some(email),
        Some("/enterEmailUrl"),
        Some("/back"),
        Some("title"),
        "passcode",
        1,
        passcodesSentToEmail = 1,
        passcodeAttempts     = 0
      )

      whenReady(repository.initialise(testJourney)){ value =>
        whenReady(repository.submitEmail(journeyId, someOtherEmail)) { journey =>
          journey shouldBe Some(testJourney.copy(emailAddress         = Some(someOtherEmail), passcodesSentToEmail = 1, emailAddressAttempts = 2))
        }
      }
    }
  }

}
