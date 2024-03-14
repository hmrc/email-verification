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

import com.mongodb.MongoException
import config.AppConfig
import org.mongodb.scala.bson.{BsonBoolean, BsonString}
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant

class VerifiedHashedEmailMongoRepositorySpec extends RepositoryBaseSpec {

  val email = "user@email.com"
  val anotherEmail = "another.user@email.com"
  def emailWithNumber(num: Int) = s"user$num@email.com"
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConfig: AppConfig = mock[AppConfig]
  when(mockConfig.verifiedEmailRepoHashKey).thenReturn("somehashkey")

  lazy val repository = new VerifiedHashedEmailMongoRepository(mongoComponent, mockConfig)

  "insert" should {
    "insert a document when it does not exist" in {
      repository.find(email).futureValue shouldBe None
      await(repository.insert(email))

      val docs = await(repository.find(email))
      docs shouldBe Some(VerifiedEmail(email))
    }

    "blow up if the email already exists" in {
      await(repository.ensureIndexes())
      await(repository.insert(email))
      val result = intercept[MongoException](await(repository.insert(email)))
      result.getCode shouldBe 11000
    }

    "not blow up if another email exists" in {
      repository.find(email).futureValue shouldBe None
      await(repository.insert(email))
      await(repository.insert(anotherEmail))

      repository.find(email).futureValue        shouldBe Some(VerifiedEmail(email))
      repository.find(anotherEmail).futureValue shouldBe Some(VerifiedEmail(anotherEmail))
    }

  }

  "find" should {
    "return verified email if it exist" in {
      repository.find(email).futureValue shouldBe None
      await(repository.insert(email))

      await(repository.find(email)) shouldBe Some(VerifiedEmail(email))
    }

    "return None if email does not exist" in {
      await(repository.find(email)) shouldBe None
    }
  }

  "isVerified" should {
    "return true if verified email exist" in {
      repository.find(email).futureValue shouldBe None
      await(repository.insert(email))

      await(repository.isVerified(email)) shouldBe true
    }

    "return false if email does not exist" in {
      await(repository.isVerified(email)) shouldBe false
    }
  }

  "ensureIndexes" should {
    "verify indexes exist" in {
      await(repository.ensureIndexes())
      val indexes = repository.collection
        .listIndexes()
        .toFuture()
        .futureValue
        .map(_.toBsonDocument)
        .filter(_.get("name") == BsonString("emailUnique"))
        .filter(_.get("key").toString.contains("hashedEmail"))
        .filter(_.get("unique") == BsonBoolean(true))
      indexes.size shouldBe 1
    }
  }

  "insertBatch" should {
    "Add a number of hashed verified email records" in {
      val emails = (0 to 9).map(emailWithNumber)
      emails.map(email => await(repository.find(email)) shouldBe None)
      val verifiedEmails = emails.map(email => (VerifiedEmail(email), Instant.now()))
      val insertedCount = await(repository.insertBatch(verifiedEmails))
      insertedCount shouldBe 10
      emails.map(email => await(repository.find(email)) shouldBe Some(VerifiedEmail(email)))
    }

    "Add a number of hashed verified email records, where one already exists" in {
      await(repository.ensureIndexes())
      val emails = (0 to 9).map(emailWithNumber)
      emails.map(email => await(repository.find(email)) shouldBe None)

      await(repository.insert(emailWithNumber(5)))
      await(repository.find(emailWithNumber(5))) shouldBe defined
      Thread.sleep(500) // allow time for index update

      val verifiedEmails = emails.map(email => (VerifiedEmail(email), Instant.now()))
      val insertedCount = await(repository.insertBatch(verifiedEmails))
      insertedCount shouldBe 9
      emails.map(email => await(repository.find(email)) shouldBe Some(VerifiedEmail(email)))
    }
  }
  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.ensureIndexes())
  }
}
