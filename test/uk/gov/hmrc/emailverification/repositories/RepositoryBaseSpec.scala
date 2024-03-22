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

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import uk.gov.hmrc.gg.test.UnitSpec
import uk.gov.hmrc.mongo.test.IndexedMongoQueriesSupport

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext

trait RepositoryBaseSpec extends UnitSpec with BeforeAndAfterEach with ScalaFutures with IndexedMongoQueriesSupport with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  // Increase timeout used by ScalaFutures when awaiting completion of futures
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(1, Seconds))

  val clock: Clock = Clock.fixed(Instant.now, ZoneId.systemDefault)

  override def beforeEach(): Unit = {
    dropDatabase()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    dropDatabase()
  }
}
