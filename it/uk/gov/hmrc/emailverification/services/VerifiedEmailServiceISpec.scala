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

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.emailverification.repositories.{VerifiedEmailMongoRepository, VerifiedHashedEmailMongoRepository}
import uk.gov.hmrc.gg.test.LogCapturing
import uk.gov.hmrc.http.HeaderCarrier

class VerifiedEmailServiceISpec extends AnyWordSpec with should.Matchers with GuiceOneAppPerSuite with FutureAwaits with DefaultAwaitTimeout with ScalaCheckPropertyChecks with LogCapturing {
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val oldRepo = app.injector.instanceOf[VerifiedEmailMongoRepository]
  private val newRepo = app.injector.instanceOf[VerifiedHashedEmailMongoRepository]
  private val service = app.injector.instanceOf[VerifiedEmailService]

  "insert(mixedCaseEmail: String)" should {
    val caseMismatchMsg = "Case clash - ignoring dup key error"

    s"log \"$caseMismatchMsg\" when both repos have same email but in different cases" in
      withCaptureOfLoggingFrom[VerifiedEmailService] { logEvents =>
        forAll { scenario: CaseMismatchScenario =>
          await(oldRepo.collection.drop().toFuture())
          await(oldRepo.ensureIndexes)
          await(newRepo.collection.drop().toFuture())
          await(newRepo.ensureIndexes)

          await(oldRepo.insert(scenario.oldRepoEmail))
          await(newRepo.insert(scenario.newRepoEmail))

          await(service.insert(scenario.newRepoEmail))
          logEvents.last.getMessage should include(caseMismatchMsg)
        }
      }
  }
}
