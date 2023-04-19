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
    "increment the passcode, but not return the incremented value in the journey" in {
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
        0,
        passcodesSentToEmail = 0,
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

}
