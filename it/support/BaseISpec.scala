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

package support

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{noContent, post, stubFor}
import config.AppConfig
import org.scalatest.GivenWhenThen
import play.api.Configuration
import support.EmailStub._
import uk.gov.hmrc.emailverification.repositories._
import uk.gov.hmrc.gg.test.WireMockSpec
import uk.gov.hmrc.mongo.test.MongoSupport
import scala.concurrent.ExecutionContext.Implicits.global

trait BaseISpec extends WireMockSpec with MongoSupport with GivenWhenThen {

  override def extraConfig: Map[String, Any] = Map(
    "play.http.router" -> "testOnlyDoNotUseInAppConf.Routes",
    "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
    "mongodb.uri" -> mongoUri,
    "maxPasscodeAttempts" -> maxPasscodeAttempts,
    "verificationStatusRepositoryTtl" -> "24 hours",
    "maxDifferentEmails" -> 5
  )

  override def dropDatabase(): Unit =
    await(mongoDatabase
      .drop()
      .toFuture())

  def appClient(path: String): String = resource(s"/email-verification$path")

  val config = Configuration.from(extraConfig)
  val appConfig: AppConfig = new AppConfig(config)
  implicit val implicitConfig = config.underlying

  def tokenFor(email: String): String = {
    expectEmailToBeSent()

    await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify = email))).status shouldBe 201
    decryptedToken(lastVerificationEmail)._1.get
  }

  val maxPasscodeAttempts = 5

  lazy val verificationStatusRepo = new VerificationStatusMongoRepository(mongoComponent, config = appConfig)
  lazy val journeyRepo =new JourneyMongoRepository(mongoComponent)
  lazy val passcodeRepo = new PasscodeMongoRepository(mongoComponent = mongoComponent, config = appConfig)

  override def beforeEach(): Unit = {
    super.beforeEach()
    WireMock.reset()

    dropDatabase()
    AnalyticsStub.stubAnalyticsEvent()
    stubFor(post("/write/audit").willReturn(noContent))
    stubFor(post("/write/audit/merged").willReturn(noContent))
  }

  override def afterAll(): Unit = {
    super.afterAll()
    dropDatabase()
  }
}
