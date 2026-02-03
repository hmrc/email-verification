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

package uk.gov.hmrc.emailverification.controllers

import com.typesafe.config.Config
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.EmailStub._
import support.IntegrationBaseSpec
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import play.api.libs.ws.writeableOf_JsValue

class TokenValidationISpec extends IntegrationBaseSpec {

  implicit lazy val config: Config = Configuration.from(serviceConfig).underlying

  def tokenFor(email: String): String = {
    expectEmailToBeSent()

    await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify = email))).status shouldBe 201
    decryptedToken(lastVerificationEmail)._1.get
  }

  "validating the token" should {
    "return 201 if the token is valid and 204 if email is already verified" in {
      val token = tokenFor("user@email.com")
      val response = await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token)))
      response.status shouldBe 201

      val response2 = await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token)))
      response2.status shouldBe 204
    }

    "return 400 if the token is invalid or expired" in {
      val response = await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> "invalid")))
      response.status                                 shouldBe 400
      (Json.parse(response.body) \ "code").as[String] shouldBe "TOKEN_NOT_FOUND_OR_EXPIRED"
    }
  }

  "getting verified email" should {

    "return 404 if email does not exist" in {
      val email = "user@email.com"
      expectEmailToBeSent()
      await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify = email)))

      val response = await(wsClient.url(appClient(s"/verified-email-addresses/$email")).get())
      response.status shouldBe 404
    }
  }

  it should {
    "return verified email if it exist" in {
      val email = "user@email.com"
      val token = tokenFor(email)
      await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token)))

      val response = await(wsClient.url(appClient("/verified-email-check")).post(Json.obj("email" -> "user@email.com")))

      response.status                               shouldBe 200
      VerifiedEmail.format.reads(response.json).get shouldBe VerifiedEmail(email)
    }

    "lower case email addresses in hashed verified email repository" in {
      val email = "user@email.com"
      val token = tokenFor(email)
      await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token)))

      val response = await(wsClient.url(appClient("/verified-email-check")).post(Json.obj("email" -> "user@email.com".toUpperCase)))

      response.status                               shouldBe 200
      VerifiedEmail.format.reads(response.json).get shouldBe VerifiedEmail(email)
    }
  }
}
