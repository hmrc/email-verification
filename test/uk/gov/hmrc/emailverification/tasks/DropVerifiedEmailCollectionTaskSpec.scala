/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.emailverification.tasks

import config.AppConfig
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.emailverification.repositories.VerifiedEmailMongoRepository

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
class DropVerifiedEmailCollectionTaskSpec extends TestKit(ActorSystem("DropEmailVerification")) with AnyWordSpecLike with MockitoSugar with Eventually {
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))
  trait Setup {
    val appConfigMock = mock[AppConfig]
    val mockRepo = mock[VerifiedEmailMongoRepository]
  }
  "DropVerifiedEmailCollectionTask" should {
    "drop the collection" in new Setup {
      when(appConfigMock.dropVerifiedEmailCollectionEnabled).thenReturn(true)
      when(appConfigMock.dropVerifiedEmailCollectionDelaySecs).thenReturn(1)
      when(mockRepo.drop()).thenReturn(Future.unit)
      new DropVerifiedEmailCollectionTask(appConfigMock, mockRepo)
      eventually {
        verify(mockRepo).drop()
        succeed
      }
    }
  }
}
