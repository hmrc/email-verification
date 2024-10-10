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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

class EmailVerificationV2ControllerISpec extends PlaySpec with GuiceOneServerPerSuite with ScalaFutures {
  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
      )
      .build()

  implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(5, Seconds), Span(5, Seconds))

  "EmailVerificationV2Controller" when {
    "given a valid email" should {
      "sendCode when a valid email is provided" in {
        val response = wsUrl(s"/email-verification/v2/send-code")
          .post(Json.parse(s"""{"email":"joe@bloggs.com"}"""))
          .futureValue

        response.status mustBe Status.OK
        (response.json \ "code").as[String] mustBe "CODE_SENT"
      }

      "verify code successfully when provided the correct verification code" in {
        val retrieveVerificationCodeResponse = wsUrl(s"/test-only/retrieve/verification-code")
          .post(Json.parse("""{"email":"joe@bloggs.com"}"""))
          .futureValue
        retrieveVerificationCodeResponse.status mustBe Status.OK
        val verificationCode = (retrieveVerificationCodeResponse.json \ "code").as[String]

        val response = wsUrl(s"/email-verification/v2/verify-code")
          .post(Json.parse(s"""{"email":"joe@bloggs.com", "verificationCode":"$verificationCode"}"""))
          .futureValue
        response.status mustBe Status.OK
        (response.json \ "code").as[String] mustBe "CODE_VERIFIED"
      }
    }

    "given an invalid email" should {
      "sendCode when an valid email is provided" in {
        val response = wsUrl(s"/email-verification/v2/send-code")
          .post(Json.parse(s"""{"email":"invalid-email"}"""))
          .futureValue

        response.status mustBe Status.BAD_REQUEST
      }
    }
  }
}
