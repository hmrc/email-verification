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

import com.mongodb.MongoException
import config.{AppConfig, WhichToUse}
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import uk.gov.hmrc.emailverification.repositories.{RepositoryBaseSpec, VerifiedEmailMongoRepository, VerifiedHashedEmailMongoRepository}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class VerifiedEmailServiceSpec extends RepositoryBaseSpec {

  val lowerCaseEmail = "user@email.com"
  val mixedCaseEmail = "uSeR@eMaiL.com"
  val anotherEmail = "another.user@email.com"
  def emailWithNumber(num: Int) = s"user$num@email.com"
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConfig: AppConfig = mock[AppConfig]
  when(mockConfig.verifiedEmailRepoHashKey).thenReturn("somehashkey")

  lazy val emailRepo = new VerifiedEmailMongoRepository(mongoComponent)
  lazy val hashedEmailRepo = new VerifiedHashedEmailMongoRepository(mongoComponent, mockConfig)
  lazy val service = new VerifiedEmailService(emailRepo, hashedEmailRepo, mockConfig)

  "insert" should {
    "insert a document into both repos when verifiedEmailCheckCollection is Both" in {
      when(mockConfig.verifiedEmailUpdateCollection).thenReturn(WhichToUse.Both)
      await(emailRepo.find(lowerCaseEmail)) shouldBe None
      await(hashedEmailRepo.find(lowerCaseEmail)) shouldBe None

      await(service.insert(lowerCaseEmail))

      val emailRepoDoc = await(emailRepo.find(lowerCaseEmail))
      emailRepoDoc shouldBe Some(VerifiedEmail(lowerCaseEmail))
      val hashedEmailRepoDoc = await(hashedEmailRepo.find(lowerCaseEmail))
      hashedEmailRepoDoc shouldBe Some(VerifiedEmail(lowerCaseEmail))
    }

    "insert a document into hashed emails repo only when verifiedEmailCheckCollection is New" in {
      when(mockConfig.verifiedEmailUpdateCollection).thenReturn(WhichToUse.New)
      await(emailRepo.find(lowerCaseEmail)) shouldBe None
      await(hashedEmailRepo.find(lowerCaseEmail)) shouldBe None

      await(service.insert(lowerCaseEmail))

      val emailRepoDoc = await(emailRepo.find(lowerCaseEmail))
      emailRepoDoc shouldBe None
      val hashedEmailRepoDoc = await(hashedEmailRepo.find(lowerCaseEmail))
      hashedEmailRepoDoc shouldBe Some(VerifiedEmail(lowerCaseEmail))
    }

    "blow up if the email already exists" in {
      when(mockConfig.verifiedEmailUpdateCollection).thenReturn(WhichToUse.Both)
      await(emailRepo.ensureIndexes)
      await(hashedEmailRepo.ensureIndexes)
      await(service.insert(lowerCaseEmail))
      val result = intercept[MongoException](await(service.insert(lowerCaseEmail)))
      result.getCode shouldBe 11000
    }

    "not blow up if a different email exists" in {
      when(mockConfig.verifiedEmailUpdateCollection).thenReturn(WhichToUse.Both)
      await(emailRepo.ensureIndexes)

      await(emailRepo.find(lowerCaseEmail)) shouldBe None
      await(emailRepo.find(anotherEmail)) shouldBe None

      await(service.insert(lowerCaseEmail))
      await(service.insert(anotherEmail))

      await(emailRepo.find(lowerCaseEmail)) shouldBe Some(VerifiedEmail(lowerCaseEmail))
      await(emailRepo.find(anotherEmail)) shouldBe Some(VerifiedEmail(anotherEmail))
    }

  }

  "find" should {
    "return verified email if it exists when checking the old plain text collection" in {
      when(mockConfig.verifiedEmailCheckCollection).thenReturn(WhichToUse.Old)
      service.find(lowerCaseEmail).futureValue shouldBe None
      await(emailRepo.insert(lowerCaseEmail))
      await(service.find(lowerCaseEmail)) shouldBe Some(VerifiedEmail(lowerCaseEmail))
    }

    "return verified email if it exists when checking the new hashed email collection" in {
      when(mockConfig.verifiedEmailCheckCollection).thenReturn(WhichToUse.New)
      service.find(lowerCaseEmail).futureValue shouldBe None
      await(hashedEmailRepo.insert(lowerCaseEmail))

      await(service.find(lowerCaseEmail)) shouldBe Some(VerifiedEmail(lowerCaseEmail))
    }

    "return lowercase verified email if it exists when checking for mixed case email in the new hashed email collection" in {
      when(mockConfig.verifiedEmailCheckCollection).thenReturn(WhichToUse.New)
      service.find(mixedCaseEmail).futureValue shouldBe None
      await(hashedEmailRepo.insert(lowerCaseEmail))

      await(service.find(mixedCaseEmail)) shouldBe Some(VerifiedEmail(lowerCaseEmail))
    }

    "return verified email if checking both collections but its only in the older one" in {
      when(mockConfig.verifiedEmailCheckCollection).thenReturn(WhichToUse.Both)
      service.find(lowerCaseEmail).futureValue shouldBe None
      await(emailRepo.insert(lowerCaseEmail))

      await(service.find(lowerCaseEmail)) shouldBe Some(VerifiedEmail(lowerCaseEmail))
    }

    "return None if email does not exist" in {
      when(mockConfig.verifiedEmailCheckCollection).thenReturn(WhichToUse.Old)
      await(service.find(lowerCaseEmail)) shouldBe None
    }
  }

  "isVerified" should {
    "return false if email does not exist when checking the old plain text collection" in {
      when(mockConfig.verifiedEmailCheckCollection).thenReturn(WhichToUse.Old)
      await(service.isVerified(lowerCaseEmail)) shouldBe false
    }

    "return true if email exists when checking the old plain text collection" in {
      when(mockConfig.verifiedEmailCheckCollection).thenReturn(WhichToUse.Old)
      await(emailRepo.find(lowerCaseEmail)) shouldBe None
      await(service.isVerified(lowerCaseEmail)) shouldBe false
      await(emailRepo.insert(lowerCaseEmail))
      await(service.isVerified(lowerCaseEmail)) shouldBe true
    }

    "return false if email does not exist when checking the new hashed email collection" in {
      when(mockConfig.verifiedEmailCheckCollection).thenReturn(WhichToUse.New)
      await(service.isVerified(lowerCaseEmail)) shouldBe false
    }

    "return true if email exists when checking the new hashed email collection" in {
      when(mockConfig.verifiedEmailCheckCollection).thenReturn(WhichToUse.New)
      await(hashedEmailRepo.find(lowerCaseEmail)) shouldBe None
      await(service.isVerified(lowerCaseEmail)) shouldBe false
      await(hashedEmailRepo.insert(lowerCaseEmail))
      await(service.isVerified(lowerCaseEmail)) shouldBe true
    }

    "return true if email only exists in the old plain text email collection when checking both collections" in {
      when(mockConfig.verifiedEmailCheckCollection).thenReturn(WhichToUse.Both)
      await(emailRepo.find(lowerCaseEmail)) shouldBe None
      await(hashedEmailRepo.find(lowerCaseEmail)) shouldBe None
      await(service.isVerified(lowerCaseEmail)) shouldBe false
      await(emailRepo.insert(lowerCaseEmail))
      await(service.isVerified(lowerCaseEmail)) shouldBe true
    }

  }

  "migrateEmailAddresses" should {
    "copy records from emailRepo to hashedEmailRepo" in {
      val emails = (0 to 999).map(emailWithNumber(_))
      await(Future.sequence(emails.map(emailRepo.insert(_))))
      await(hashedEmailRepo.isVerified(emailWithNumber(0))) shouldBe false

      when(mockConfig.emailMigrationBatchSize).thenReturn(50)
      when(mockConfig.emailMigrationBatchDelayMillis).thenReturn(10)
      when(mockConfig.emailMigrationMaxDurationSeconds).thenReturn(60)
      when(mockConfig.verifiedEmailRepoTTLDays).thenReturn(365)
      val results = await(service.migrateEmailAddresses())
      results.readCount shouldBe 1000
      results.insertedCount shouldBe 1000
      results.duplicateCount shouldBe 0
      results.expiredCount shouldBe 0

      emails.map(email => await(hashedEmailRepo.isVerified(email)) shouldBe true)
    }

    "copy records from emailRepo to hashedEmailRepo and finish part way through if max duration reached" in {
      val emails = (0 to 999).map(emailWithNumber(_))
      await(Future.sequence(emails.map(emailRepo.insert(_))))
      await(hashedEmailRepo.isVerified(emailWithNumber(0))) shouldBe false

      when(mockConfig.emailMigrationBatchSize).thenReturn(50)
      when(mockConfig.emailMigrationBatchDelayMillis).thenReturn(300)
      when(mockConfig.emailMigrationMaxDurationSeconds).thenReturn(1)
      when(mockConfig.verifiedEmailRepoTTLDays).thenReturn(365)
      val results = await(service.migrateEmailAddresses())
      results.readCount should be < 300
      results.insertedCount should be < 300
      results.duplicateCount shouldBe 0
      results.expiredCount shouldBe 0
    }

    "copy records from emailRepo to hashedEmailRepo and not blow up adding duplicate entries" in {
      await(hashedEmailRepo.ensureIndexes)
      val emails = (0 to 999).map(emailWithNumber(_))
      await(Future.sequence(emails.map(emailRepo.insert(_))))
      await(hashedEmailRepo.isVerified(emailWithNumber(0))) shouldBe false

      await(hashedEmailRepo.insert(emailWithNumber(5))) //so a duplicate is already present
      Thread.sleep(500) //allow time for index to also update
      when(mockConfig.emailMigrationBatchSize).thenReturn(50)
      when(mockConfig.emailMigrationBatchDelayMillis).thenReturn(10)
      when(mockConfig.emailMigrationMaxDurationSeconds).thenReturn(60)
      when(mockConfig.verifiedEmailRepoTTLDays).thenReturn(365)
      val results = await(service.migrateEmailAddresses())
      results.readCount shouldBe 1000
      results.insertedCount shouldBe 999
      results.duplicateCount shouldBe 1
      results.expiredCount shouldBe 0

      emails.map(email => await(hashedEmailRepo.isVerified(email)) shouldBe true)
    }

    "copy records from emailRepo to hashedEmailRepo ignoring expired records" in {
      await(hashedEmailRepo.ensureIndexes)
      val emails = (0 to 999).map(emailWithNumber(_))
      await(Future.sequence(emails.map(emailRepo.insert(_))))
      await(hashedEmailRepo.isVerified(emailWithNumber(0))) shouldBe false

      Thread.sleep(500) //allow time for index to also update
      when(mockConfig.emailMigrationBatchSize).thenReturn(50)
      when(mockConfig.emailMigrationBatchDelayMillis).thenReturn(10)
      when(mockConfig.emailMigrationMaxDurationSeconds).thenReturn(60)
      when(mockConfig.verifiedEmailRepoTTLDays).thenReturn(0)
      val results = await(service.migrateEmailAddresses())
      results.readCount shouldBe 1000
      results.insertedCount shouldBe 0
      results.duplicateCount shouldBe 0
      results.expiredCount shouldBe 1000

      emails.map(email => await(hashedEmailRepo.isVerified(email)) shouldBe false)
    }

  }

  override def beforeEach() = {
    super.beforeEach()
    await(emailRepo.ensureIndexes)
    await(hashedEmailRepo.ensureIndexes)
  }
}
