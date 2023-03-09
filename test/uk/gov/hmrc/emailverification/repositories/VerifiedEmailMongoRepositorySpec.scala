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
import org.mongodb.scala.bson.{BsonBoolean, BsonString}
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant

class VerifiedEmailMongoRepositorySpec extends RepositoryBaseSpec {

  val email = "user@email.com"
  val anotherEmail = "another.user@email.com"
  def emailWithNumber(num: Int) = s"user$num@email.com"
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val repository = new VerifiedEmailMongoRepository(mongoComponent)

  "insert" should {
    "insert a document when it does not exist" in {
      repository.find(email).futureValue shouldBe None
      await(repository.insert(email))

      val docs = repository.find(email).futureValue
      docs shouldBe Some(VerifiedEmail(email))
    }

    "blow up if the email already exists" in {
      await(repository.ensureIndexes)
      await(repository.insert(email))
      val result = intercept[MongoException](await(repository.insert(email)))
      result.getCode shouldBe 11000
    }

    "not blow up if another email exists" in {
      repository.find(email).futureValue shouldBe None
      await(repository.insert(email))
      await(repository.insert(anotherEmail))

      repository.find(email).futureValue shouldBe Some(VerifiedEmail(email))
      repository.find(anotherEmail).futureValue shouldBe Some(VerifiedEmail(anotherEmail))
    }

  }

  "find" should {
    "return verified email if it exist" in {
      repository.find(email).futureValue shouldBe None
      await(repository.insert(email))

      repository.find(email).futureValue shouldBe Some(VerifiedEmail(email))
    }

    "return None if email does not exist" in {
      repository.find(email).futureValue shouldBe None
    }
  }

  "isVerified" should {
    "return true if verified email exist" in {
      repository.find(email).futureValue shouldBe None
      await(repository.insert(email))

      repository.isVerified(email).futureValue shouldBe true
    }

    "return false if email does not exist" in {
      repository.isVerified(email).futureValue shouldBe false
    }
  }

  "ensureIndexes" should {
    "verify indexes exist" in {
      await(repository.ensureIndexes)
      val indexes = repository.collection.listIndexes().toFuture().futureValue
        .map(_.toBsonDocument)
        .filter(_.get("name") == BsonString("emailUnique"))
        .filter(_.get("key").toString.contains("email"))
        .filter(_.get("unique") == BsonBoolean(true))

      indexes.size shouldBe 1
    }
  }

  "getBatch" should {
    "fetch correct list of records" in {
      val emails = (0 to 24).map(emailWithNumber(_))
      emails.map(email => await(repository.insert(email)))
      val firstBatch = await(repository.getBatch(0, 5))
      firstBatch.size shouldBe 5
      firstBatch.head._2.isAfter(Instant.now().minusSeconds(2)) shouldBe true
      firstBatch.map(_._1) should contain(VerifiedEmail(emailWithNumber(1)))
      val lastBatch = await(repository.getBatch(20, 5))
      lastBatch.size shouldBe 5
      (20 to 24).map { index =>
        lastBatch.map(_._1) should contain(VerifiedEmail(emailWithNumber(index)))
      }
    }

  }
}
