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

import org.mockito.Mockito.*
import config.AppConfig
import uk.gov.hmrc.emailverification.models.VerificationCodeMongoDoc
import uk.gov.hmrc.mongo.CurrentTimestampSupport

import java.time.Clock
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class VerificationCodeV2MongoRepositorySpec extends RepositoryBaseSpec {

  val config: AppConfig = mock[AppConfig]
  when(config.appName).thenReturn("test-application")

  val repository = new VerificationCodeV2MongoRepository(mongoComponent, config, new CurrentTimestampSupport())

  val clock2: Clock = Clock.offset(clock, java.time.Duration.ofNanos(1.minute.toNanos))
  val clock3: Clock = Clock.offset(clock, java.time.Duration.ofNanos(2.minute.toNanos))

  val verificationCode1 = "verification-code-123"
  val verificationCode2 = "verification-code-456"
  val email = "user@email.com"

  "put" should {
    "add record to repository" in {
      Await.ready(repository.put(email)(
                    VerificationCodeV2MongoRepository.emailVerificationCodeDataDataKey,
                    VerificationCodeMongoDoc(email, verificationCode1)
                  ),
                  5.seconds
                 )
      val cacheDocMaybe = Await.result(repository.get(email)(VerificationCodeV2MongoRepository.emailVerificationCodeDataDataKey), 5.seconds)

      cacheDocMaybe shouldBe defined
      val cacheDoc = cacheDocMaybe.get
      cacheDoc.email            shouldBe email
      cacheDoc.verificationCode shouldBe verificationCode1
    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.ensureIndexes())
  }
}
