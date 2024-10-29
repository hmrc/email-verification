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
import com.typesafe.config.Config
import config.AppConfig
import org.scalatest.GivenWhenThen
import org.scalatest.time.{Seconds, Span}
import play.api.Configuration
import support.EmailStub._
import uk.gov.hmrc.emailverification.repositories._
import uk.gov.hmrc.gg.test.WireMockSpec
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

trait BaseISpec extends WireMockSpec with MongoSupport with GivenWhenThen {

  // Increase timeout used by ScalaFutures when awaiting completion of futures
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(4, Seconds), interval = Span(1, Seconds))

  override def extraConfig: Map[String, Any] = Map(
    "appName"                                              -> "test-app",
    "play.http.router"                                     -> "testOnlyDoNotUseInAppConf.Routes",
    "queryParameter.encryption.key"                        -> "gvBoGdgzqG1AarzF1LY0zQ==",
    "mongodb.uri"                                          -> mongoUri,
    "maxPasscodeAttempts"                                  -> maxPasscodeAttempts,
    "verificationStatusRepositoryTtl"                      -> "24 hours",
    "maxDifferentEmails"                                   -> 5,
    "microservice.services.access-control.request.formUrl" -> "access-request-form-url",
    "microservice.services.access-control.enabled"         -> "false",
    "microservice.services.access-control.allow-list"      -> List()
  )

  override def dropDatabase(): Unit =
    await(
      mongoDatabase
        .drop()
        .toFuture()
    )

  def appClient(path: String): String = resource(s"/email-verification$path")

  val config: Configuration = Configuration.from(extraConfig)
  val appConfig: AppConfig = new AppConfig(config)
  implicit val implicitConfig: Config = config.underlying

  def tokenFor(email: String): String = {
    expectEmailToBeSent()

    await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify = email))).status shouldBe 201
    decryptedToken(lastVerificationEmail)._1.get
  }

  val maxPasscodeAttempts = 5

  lazy val verificationStatusRepo = new VerificationStatusMongoRepository(mongoComponent, config = appConfig)
  lazy val journeyRepo = new JourneyMongoRepository(mongoComponent)
  lazy val passcodeRepo = new PasscodeMongoRepository(mongoComponent = mongoComponent, config = appConfig)

  override def beforeEach(): Unit = {
    super.beforeEach()
    WireMock.reset()

    dropDatabase()
    stubFor(post("/write/audit").willReturn(noContent))
    stubFor(post("/write/audit/merged").willReturn(noContent))
  }

  override def afterAll(): Unit = {
    super.afterAll()
    dropDatabase()
  }

}
