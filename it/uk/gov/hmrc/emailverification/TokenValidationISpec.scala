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

package uk.gov.hmrc.emailverification

import play.api.libs.json.Json
import support.BaseISpec
import support.EmailStub._
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import uk.gov.hmrc.http.HeaderCarrier

class TokenValidationISpec extends BaseISpec {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  "validating the token" should {
    "return 201 if the token is valid and 204 if email is already verified" in {
      Given("a verification request exists")
      val token = tokenFor("user@email.com")

      When("a token verification request is submitted")
      val response = await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token)))

      Then("Service responds with Created")
      response.status shouldBe 201

      When("the same token is submitted again")
      val response2 = await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token)))

      Then("Service responds with NoContent")
      response2.status shouldBe 204
    }

    "return 400 if the token is invalid or expired" in {
      When("an invalid token verification request is submitted")
      val response = await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> "invalid")))
      response.status shouldBe 400
      (Json.parse(response.body) \ "code").as[String] shouldBe "TOKEN_NOT_FOUND_OR_EXPIRED"
    }
  }

  "getting verified email" should {

    "return 404 if email does not exist" in {
      Given("an unverified email does not  exist")
      val email = "user@email.com"
      expectEmailToBeSent()
      await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify = email)))

      When("the email is checked if it is verified")
      val response = await(wsClient.url(appClient(s"/verified-email-addresses/$email")).get())
      Then("404 is returned")
      response.status shouldBe 404
    }
  }

  it should {
    "return verified email if it exist" in {
      Given("a verified email already exist")
      val email = "user@email.com"
      val token = tokenFor(email)
      await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token)))

      When("an email is checked if it is verified")
      val response = await(wsClient.url(appClient("/verified-email-check")).post(Json.obj("email" -> "user@email.com")))

      Then("email resource is returned")
      response.status shouldBe 200
      VerifiedEmail.format.reads(response.json).get shouldBe VerifiedEmail(email)
    }
  }
}
