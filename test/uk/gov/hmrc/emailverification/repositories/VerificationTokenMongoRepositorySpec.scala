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

import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import uk.gov.hmrc.emailverification.models.VerificationDoc
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoUtils

import java.time.temporal.ChronoUnit
import java.time.{Clock, Duration, Instant}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt

class VerificationTokenMongoRepositorySpec extends RepositoryBaseSpec with Eventually {

  lazy val repository = new VerificationTokenMongoRepository(mongoComponent)
  val token = "theToken"
  val email = "user@email.com"
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val tomorrowClock = Clock.offset(clock, java.time.Duration.ofNanos(1.day.toNanos))

  "upsert" should {
    "always update the existing document for a given email address" in {

      val token1 = token + "1"
      repository.collection.find().toFuture().futureValue shouldBe Nil
      await (repository.upsert(token1, email, Duration.ofDays(1), clock))
      repository.collection.find().toFuture().futureValue shouldBe Seq(VerificationDoc(email, token1, Instant.now(tomorrowClock).truncatedTo(ChronoUnit.MILLIS)))

      val token2 = token + "2"
      await (repository.upsert(token2, email, Duration.ofDays(1), clock))
      repository.collection.find().toFuture().futureValue shouldBe Seq(VerificationDoc(email, token2, Instant.now(tomorrowClock).truncatedTo(ChronoUnit.MILLIS)))
    }
  }

  "find" should {
    "return the verification document" in {
      await (repository.upsert(token, email, Duration.ofDays(1), clock))
      val doc = repository.findToken(token).futureValue
      doc shouldBe Some(VerificationDoc(email, token, Instant.now(tomorrowClock).truncatedTo(ChronoUnit.MILLIS)))
    }

    "return None whet token does not exist or has expired" in {
      repository.findToken(token).futureValue shouldBe None
    }
  }

  "VerificationTokenMongoRepository" should {
    "delete record after TTL expires" in {
      MongoUtils.ensureIndexes(repository.collection,
                               Seq(
          IndexModel(Indexes.ascending("expireAt"), IndexOptions().name("expireAtIndex").expireAfter(1, TimeUnit.SECONDS))
        ), true)

      await (repository.upsert(token, email, Duration.ofDays(0), clock))
      repository.collection.find().toFuture().futureValue shouldBe Seq(VerificationDoc(email, token, Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)))

      eventually(timeout(Span(120, Seconds)))(
        repository.collection.find().toFuture().futureValue shouldBe Nil
      )
    }
  }

}
