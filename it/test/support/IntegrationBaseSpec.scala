/*
 * Copyright 2025 HM Revenue & Customs
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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.emailverification.repositories.{JourneyMongoRepository, PasscodeMongoRepository, VerificationStatusMongoRepository}
import uk.gov.hmrc.emailverification.support.WireMockHelper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext

trait IntegrationBaseSpec
    extends AnyWordSpec
    with Matchers
    with WireMockHelper
    with MockitoSugar
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with MongoSupport {

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  lazy val passcodeRepo = new PasscodeMongoRepository(mongoComponent = mongoComponent, config = app.injector.instanceOf[AppConfig])

  lazy val journeyRepo = new JourneyMongoRepository(mongoComponent)

  lazy val verificationStatusRepo = new VerificationStatusMongoRepository(mongoComponent, config = app.injector.instanceOf[AppConfig])

  def wsClient: WSClient = app.injector.instanceOf[WSClient]

  def resource(resource: String) = s"http://localhost:$port$resource"

  def resourceRequest(url: String): WSRequest = wsClient.url(resource(url)).withHttpHeaders("Csrf-Token" -> "nocheck")

  def appClient(path: String): String = resource(s"/email-verification$path")

  val maxPasscodeAttempts = 5

  def serviceConfig: Map[String, Any] = Map(
    "appName"                                              -> "test-app",
    "play.http.router"                                     -> "testOnlyDoNotUseInAppConf.Routes",
    "queryParameter.encryption.key"                        -> "gvBoGdgzqG1AarzF1LY0zQ==",
    "mongodb.uri"                                          -> mongoUri,
    "maxPasscodeAttempts"                                  -> maxPasscodeAttempts,
    "verificationStatusRepositoryTtl"                      -> "24 hours",
    "maxDifferentEmails"                                   -> 5,
    "microservice.services.access-control.request.formUrl" -> "access-request-form-url",
    "microservice.services.access-control.enabled"         -> "false",
    "microservice.services.access-control.allow-list"      -> List(),
    "microservice.services.auth.port"                      -> WireMockHelper.wireMockPort,
    "microservice.services.email.port"                     -> WireMockHelper.wireMockPort
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder().configure(serviceConfig).build()

  override def dropDatabase(): Unit = await(mongoDatabase.drop().toFuture())

  override def beforeAll(): Unit = {
    super.beforeAll()
    startWireMock()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    stopWireMock()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    WireMock.reset()

    dropDatabase()

    stubFor(post("/write/audit").willReturn(noContent))
    stubFor(post("/write/audit/merged").willReturn(noContent))
  }

}
