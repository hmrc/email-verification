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
import uk.gov.hmrc.emailverification.models.PasscodeDoc
import uk.gov.hmrc.http.SessionId

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}
import scala.concurrent.duration.DurationInt

class PasscodeMongoRepositorySpec extends RepositoryBaseSpec {

  val config: AppConfig = mock[AppConfig]
  when(config.maxDifferentEmails).thenReturn(2)

  val repository = new PasscodeMongoRepository(mongoComponent, config)

  val clock2 = Clock.offset(clock, java.time.Duration.ofNanos(1.minute.toNanos))
  val clock3 = Clock.offset(clock, java.time.Duration.ofNanos(2.minute.toNanos))

  val sessionId = "session123"
  val passcode1 = "pass123"
  val passcode2 = "pass456"
  val email = "user@email.com"

  "upsertIncrementingEmailAttempts" should {
    "update passcode and increment emailAttempts" in {
      val doc = repository.upsertIncrementingEmailAttempts(SessionId(sessionId), passcode1, email, 1, clock).futureValue
      val doc2 = repository.upsertIncrementingEmailAttempts(SessionId(sessionId), passcode2, email, 1, clock2).futureValue

      doc shouldBe PasscodeDoc(sessionId, email, passcode1.toUpperCase, Instant.now(clock2).truncatedTo(ChronoUnit.MILLIS), 0, 1)
      doc2 shouldBe PasscodeDoc(sessionId, email, passcode2.toUpperCase, Instant.now(clock3).truncatedTo(ChronoUnit.MILLIS), 0, 2)
    }
  }

  "getSessionEmailsCount" should {
    "return correct count" in {
      repository.getSessionEmailsCount(SessionId(sessionId)).futureValue shouldBe 0
      await(repository.upsertIncrementingEmailAttempts(SessionId(sessionId), passcode1, email, 1, clock))
      repository.getSessionEmailsCount(SessionId(sessionId)).futureValue shouldBe 1
    }
  }

  "findPasscodesBySessionId" should {
    "return correct PasscodeDoc" in {
      repository.findPasscodesBySessionId(sessionId).futureValue shouldBe Nil
      await(repository.upsertIncrementingEmailAttempts(SessionId(sessionId), passcode1, email, 1, clock))
      val expectedPasscodeDoc = PasscodeDoc(sessionId, email, passcode1.toUpperCase, Instant.now(clock2).truncatedTo(ChronoUnit.MILLIS), 0, 1)
      repository.findPasscodesBySessionId(sessionId).futureValue shouldBe Seq(expectedPasscodeDoc)
    }
  }

  "findPasscodeBySessionIdAndEmail" should {
    "return correct PasscodeDoc" in {
      repository.findPasscodeBySessionIdAndEmail(sessionId, email).futureValue shouldBe Nil
      await(repository.upsertIncrementingEmailAttempts(SessionId(sessionId), passcode1, email, 1, clock))
      val expectedPasscodeDoc = PasscodeDoc(sessionId, email, passcode1.toUpperCase, Instant.now(clock2).truncatedTo(ChronoUnit.MILLIS), 0, 1)
      repository.findPasscodeBySessionIdAndEmail(sessionId, email).futureValue shouldBe Seq(expectedPasscodeDoc)
    }
  }

  "findPasscodeAndIncrementAttempts" should {
    "return correct PasscodeDoc" in {
      repository.findPasscodeAndIncrementAttempts(SessionId(sessionId), email).futureValue shouldBe None
      await(repository.upsertIncrementingEmailAttempts(SessionId(sessionId), passcode1, email, 1, clock))
      val expectedPasscodeDoc = PasscodeDoc(sessionId, email, passcode1.toUpperCase, Instant.now(clock2).truncatedTo(ChronoUnit.MILLIS), 1, 1)
      repository.findPasscodeAndIncrementAttempts(SessionId(sessionId), email).futureValue shouldBe Some(expectedPasscodeDoc)
    }
  }

  override def beforeEach() = {
    super.beforeEach()
    await(repository.ensureIndexes)
  }
}
