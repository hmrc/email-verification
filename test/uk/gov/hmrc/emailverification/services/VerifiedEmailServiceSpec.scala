/*
 * Copyright 2022 HM Revenue & Customs
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

import com.mongodb.MongoException
import config.AppConfig
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import uk.gov.hmrc.emailverification.repositories.{RepositoryBaseSpec, VerifiedEmailMongoRepository, VerifiedHashedEmailMongoRepository}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class VerifiedEmailServiceSpec extends RepositoryBaseSpec {

  val email = "user@email.com"
  val anotherEmail = "another.user@email.com"
  def emailWithNumber(num: Int) = s"user$num@email.com"
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConfig: AppConfig = mock[AppConfig]
  when(mockConfig.verifiedEmailRepoHashKey).thenReturn("somehashkey")

  lazy val emailRepo = new VerifiedEmailMongoRepository(mongoComponent)
  lazy val hashedEmailRepo = new VerifiedHashedEmailMongoRepository(mongoComponent, mockConfig)
  lazy val service = new VerifiedEmailService(emailRepo, hashedEmailRepo, mockConfig)

  "insert" should {
    "insert a document into both repos" in {
      service.find(email).futureValue shouldBe None
      await(service.insert(email))
      val doc = await(service.find(email))
      doc shouldBe Some(VerifiedEmail(email))

      val emailRepoDoc = await(emailRepo.find(email))
      emailRepoDoc shouldBe Some(VerifiedEmail(email))

      val hashedEmailRepoDoc = await(hashedEmailRepo.find(email))
      hashedEmailRepoDoc shouldBe Some(VerifiedEmail(email))
    }

    "blow up if the email already exists" in {
      await(emailRepo.ensureIndexes)
      await(hashedEmailRepo.ensureIndexes)
      await(service.insert(email))
      val result = intercept[MongoException](await(service.insert(email)))
      result.getCode shouldBe 11000
    }

    "not blow up if another email exists" in {
      service.find(email).futureValue shouldBe None
      await(service.insert(email))
      await(service.insert(anotherEmail))

      service.find(email).futureValue shouldBe Some(VerifiedEmail(email))
      service.find(anotherEmail).futureValue shouldBe Some(VerifiedEmail(anotherEmail))
    }

  }

  "find" should {
    "return verified email if it exist" in {
      service.find(email).futureValue shouldBe None
      await(service.insert(email))

      await(service.find(email)) shouldBe Some(VerifiedEmail(email))
    }

    "return None if email does not exist" in {
      await(service.find(email)) shouldBe None
    }

    "return verified email if its only in the older collection" in {
      service.find(email).futureValue shouldBe None
      await(emailRepo.insert(email))

      await(service.find(email)) shouldBe Some(VerifiedEmail(email))
    }

    "return verified email if its only in the newer collection" in {
      service.find(email).futureValue shouldBe None
      await(hashedEmailRepo.insert(email))

      await(service.find(email)) shouldBe Some(VerifiedEmail(email))
    }
  }

  "isVerified" should {
    "return true if verified email exist" in {
      service.find(email).futureValue shouldBe None
      await(service.insert(email))

      await(service.isVerified(email)) shouldBe true
    }

    "return false if email does not exist" in {
      await(service.isVerified(email)) shouldBe false
    }
  }

  "migrateEmailAddresses" should {
    "copy records from emailRepo to hashedEmailRepo" in {
      val emails = (0 to 999).map(emailWithNumber(_))
      Future.sequence(emails.map(emailRepo.insert(_)))
      await(hashedEmailRepo.isVerified(emailWithNumber(0))) shouldBe false

      when(mockConfig.emailMigrationBatchSize).thenReturn(50)
      when(mockConfig.emailMigrationBatchDelayMillis).thenReturn(10)
      when(mockConfig.emailMigrationMaxDurationSeconds).thenReturn(60)
      val migratedCount = await(service.migrateEmailAddresses())
      migratedCount shouldBe 1000

      emails.map(email => await(hashedEmailRepo.isVerified(email)) shouldBe true)
    }

    "copy records from emailRepo to hashedEmailRepo and finish part way through if max duration reached" in {
      val emails = (0 to 999).map(emailWithNumber(_))
      Future.sequence(emails.map(emailRepo.insert(_)))
      await(hashedEmailRepo.isVerified(emailWithNumber(0))) shouldBe false

      when(mockConfig.emailMigrationBatchSize).thenReturn(50)
      when(mockConfig.emailMigrationBatchDelayMillis).thenReturn(300)
      when(mockConfig.emailMigrationMaxDurationSeconds).thenReturn(1)
      val migratedCount = await(service.migrateEmailAddresses())
      migratedCount should be < 300
    }

    "copy records from emailRepo to hashedEmailRepo and not blow up adding duplicate entries" in {
      await(hashedEmailRepo.ensureIndexes)
      val emails = (0 to 999).map(emailWithNumber(_))
      Future.sequence(emails.map(emailRepo.insert(_)))
      await(hashedEmailRepo.isVerified(emailWithNumber(0))) shouldBe false

      await(hashedEmailRepo.insert(emailWithNumber(5))) //so a duplicate is already present
      Thread.sleep(500) //allow time for index to also update
      when(mockConfig.emailMigrationBatchSize).thenReturn(50)
      when(mockConfig.emailMigrationBatchDelayMillis).thenReturn(10)
      when(mockConfig.emailMigrationMaxDurationSeconds).thenReturn(60)
      val migratedCount = await(service.migrateEmailAddresses())
      migratedCount shouldBe 1000

      emails.map(email => await(hashedEmailRepo.isVerified(email)) shouldBe true)
    }
  }

}
