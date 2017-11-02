/*
 * Copyright 2017 HM Revenue & Customs
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

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

class VerifiedEmailMongoRepositorySpec extends UnitSpec with BeforeAndAfterEach with BeforeAndAfterAll with MongoSpecSupport with ScalaFutures with IntegrationPatience {
  val email = "user@email.com"
  val anotherEmail = "another.user@email.com"
  implicit val hc = HeaderCarrier()

  "insert" should {
    "insert a document when it does not exist" in {
      repo.findAll().futureValue shouldBe empty
      await(repo.insert(email))

      val docs = repo.findAll().futureValue
      docs shouldBe Seq(VerifiedEmail(email))
    }

    "blow up if the email already exists" in {
      await(repo.ensureIndexes)
      await(repo.insert(email))

      val r = intercept[DatabaseException](await(repo.insert(email)))
      r.code shouldBe Some(11000)
    }

    "not blow up if another email exists" in {
      await(repo.ensureIndexes)
      repo.findAll().futureValue shouldBe empty
      await(repo.insert(email))
      await(repo.insert(anotherEmail))

      repo.find(email).futureValue shouldBe Some(VerifiedEmail(email))
      repo.find(anotherEmail).futureValue shouldBe Some(VerifiedEmail(anotherEmail))
    }

}

  "find" should {
    "return verified email if it exist" in {
      repo.findAll().futureValue shouldBe empty
      await(repo.insert(email))

      repo.find(email).futureValue shouldBe Some(VerifiedEmail(email))
    }

    "return None if email does not exist" in {
      repo.find(email).futureValue shouldBe None
    }
  }

  "isVerified" should {
    "return true if verified email exist" in {
      repo.findAll().futureValue shouldBe empty
      await(repo.insert(email))

      repo.isVerified(email).futureValue shouldBe true
    }

    "return false if email does not exist" in {
      repo.isVerified(email).futureValue shouldBe false
    }
  }

  "ensureIndexes" should {
    "verify indexes exist" in {
      await(repo.ensureIndexes)
      val indexes = mongo().indexesManager.onCollection("verifiedEmail").list().futureValue

      val index = indexes.find(_.name.contains("emailUnique")).get

      //version of index is managed by mongodb. We don't want to assert on it.
      index shouldBe Index(Seq("email" -> Ascending), name = Some("emailUnique"), unique = true).copy(version = index.version)
    }
  }

  val repo = new VerifiedEmailMongoRepository {}

  override def beforeEach() {
    super.beforeEach()
    await(repo.drop)
  }

  override protected def afterAll() {
    await(repo.drop)
    super.afterAll()
  }
}
