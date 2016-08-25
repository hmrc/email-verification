/*
 * Copyright 2016 HM Revenue & Customs
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

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class VerifiedEmailMongoRepositorySpec extends UnitSpec with BeforeAndAfterEach with BeforeAndAfterAll with MongoSpecSupport {
  val email = "user@email.com"
  implicit val hc = HeaderCarrier()

  "insert" should {
    "insert a document when it does not exist" in {
      await(repo.find("email" -> email)) shouldBe empty
      await(repo.insert(email))

      val docs = await(repo.find("email" -> email))
      docs shouldBe Seq(VerifiedEmail(email))
    }

    "blow up if the email already exists" in {
      await(repo.ensureIndexes)
      await(repo.insert(email))

      val r = intercept[DatabaseException](await(repo.insert(email)))
      r.code shouldBe Some(11000)
    }
  }

  "ensureIndexes" should {
    "verify indexes exist" in {
      await(repo.ensureIndexes)
      val indexes = await(mongo().indexesManager.onCollection("verifiedEmail").list())

      val index = indexes.find(_.name.contains("addressUnique")).get

      //version of index is managed by mongodb. We don't want to assert on it.
      index shouldBe Index(Seq("address" -> Ascending), name = Some("addressUnique"), unique = true).copy(version = index.version)
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
