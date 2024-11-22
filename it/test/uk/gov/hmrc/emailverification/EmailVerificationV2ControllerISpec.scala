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

package uk.gov.hmrc.emailverification

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{noContent, post, stubFor}
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.{HeaderNames, Status}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.gg.test.WireMockSpec
import uk.gov.hmrc.mongo.test.MongoSupport

class EmailVerificationV2ControllerISpec extends AnyWordSpec with OptionValues with WireMockSpec with MongoSupport with GuiceOneServerPerSuite with ScalaFutures {
  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "appName"                                              -> "test-app",
        "application.router"                                   -> "testOnlyDoNotUseInAppConf.Routes",
        "microservice.services.email.port"                     -> wiremockPort,
        "microservice.services.access-control.request.formUrl" -> "access-request-form-url",
        "microservice.services.access-control.enabled"         -> "true",
        "microservice.services.access-control.allow-list"      -> List("test-user")
      )
      .build()

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(5, Seconds), Span(5, Seconds))

  override def dropDatabase(): Unit =
    await(
      mongoDatabase
        .drop()
        .toFuture()
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    WireMock.reset()

    dropDatabase()
    stubFor(post("/write/audit").willReturn(noContent))
    stubFor(post("/write/audit/merged").willReturn(noContent))
    stubFor(post("/hmrc/email").willReturn(WireMock.aResponse.withStatus(Status.ACCEPTED)))
  }

  override def afterAll(): Unit = {
    super.afterAll()
    dropDatabase()
  }

  "EmailVerificationV2Controller" when {
    "given a valid email" should {
      "sendCode when a valid email is provided" in {
        val response = resourceRequest(s"/email-verification/v2/send-code")
          .withHttpHeaders(HeaderNames.USER_AGENT -> "test-user")
          .post(Json.parse(s"""{"email":"success@sender.com"}"""))
          .futureValue

        response.status                       shouldBe Status.OK
        (response.json \ "status").as[String] shouldBe "CODE_SENT"
      }

      "verify code successfully when provided the correct verification code" in {
        val retrieveVerificationCodeResponse = resourceRequest(s"/test-only/retrieve/verification-code")
          .withHttpHeaders(HeaderNames.USER_AGENT -> "test-user")
          .post(Json.parse("""{"email":"success@sender.com"}"""))
          .futureValue
        retrieveVerificationCodeResponse.status shouldBe Status.OK
        val verificationCode = (retrieveVerificationCodeResponse.json \ "verificationCode").as[String]

        val response = resourceRequest(s"/email-verification/v2/verify-code")
          .withHttpHeaders(HeaderNames.USER_AGENT -> "test-user")
          .post(Json.parse(s"""{"email":"success@sender.com", "verificationCode":"$verificationCode"}"""))
          .futureValue
        response.status                       shouldBe Status.OK
        (response.json \ "status").as[String] shouldBe "CODE_VERIFIED"
      }
    }

    "given an invalid email" should {
      "respond with a BadRequest" in {
        val response = resourceRequest(s"/email-verification/v2/send-code")
          .withHttpHeaders(HeaderNames.USER_AGENT -> "test-user")
          .post(Json.parse(s"""{"email":"invalid-email"}"""))
          .futureValue

        response.status shouldBe Status.BAD_REQUEST
      }
    }

    "given an invalid verification code" should {
      "respond with a BadRequest" in {
        val response = resourceRequest(s"/email-verification/v2/verify-code")
          .withHttpHeaders(HeaderNames.USER_AGENT -> "test-user")
          .post(Json.parse(s"""{"email":"email@email.com", "verificationCode":"1234567"}"""))
          .futureValue

        response.status shouldBe Status.BAD_REQUEST
      }
    }

    "given a user-agent that is not on the allow list" should {
      "respond with Forbidden" in {
        val response = resourceRequest(s"/email-verification/v2/send-code")
          .withHttpHeaders(HeaderNames.USER_AGENT -> "bad-test-user")
          .post(Json.parse(s"""{"email":"good@email.com"}"""))
          .futureValue

        response.status shouldBe Status.FORBIDDEN
      }
    }
  }
}
