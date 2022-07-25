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

package uk.gov.hmrc.emailverification.repositories

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.gg.test.UnitSpec
import uk.gov.hmrc.mongo.test.MongoSupport
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext

trait RepositoryBaseSpec
  extends UnitSpec
  with MongoSupport
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with ScalaFutures {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val clock = Clock.fixed(Instant.now, ZoneId.systemDefault)

  override def beforeEach() {
    super.beforeEach()
    dropDatabase()
  }

  override def afterAll() {
    dropDatabase()
    super.afterAll()
  }

}
