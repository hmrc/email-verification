/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.Assertion
import play.api.libs.json.Json
import support.BaseISpec
import support.EmailStub._

class EmailVerificationISpec extends BaseISpec {
  val emailToVerify = "example@domain.com"

  "email verification" should {
    "send the verification email to the specified address successfully" in new Setup {
      Given("The email service is running")
      expectEmailToBeSent()

      When("a client submits a verification request")

      val response = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify, templateId, continueUrl)))
      response.status shouldBe 201

      Then("an email is sent")
      verifyEmailSentWithContinueUrl(emailToVerify, continueUrl, templateId)
    }

    "only latest email verification request token for a given email should be valid" in new Setup {
      Given("The email service is running")
      expectEmailToBeSent()

      When("client submits a verification request")
      val response1 = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify, templateId, continueUrl)))
      response1.status shouldBe 201
      val token1 = decryptedToken(lastVerificationEmail)._1.get

      When("client submits a second verification request for same email")
      val response2 = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify, templateId, continueUrl)))
      response2.status shouldBe 201
      val token2 = decryptedToken(lastVerificationEmail)._1.get

      Then("only the last verification request token should be valid")
      await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token1))).status shouldBe 400
      await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token2))).status shouldBe 201
    }

    "second verification request should return successful 204 response" in new Setup {
      Given("The email service is running")
      expectEmailToBeSent()

      When("client submits a verification request")
      val response1 = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify, templateId, continueUrl)))
      response1.status shouldBe 201
      val token = decryptedToken(lastVerificationEmail)._1.get

      Then("the verification request with the token should be successful")
      await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status shouldBe 201
      Then("an additional verification requests with the token should be successful, but return with a 204 response")
      await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status shouldBe 204
    }

    "email verification for two different emails should be successful" in new Setup {
      def submitVerificationRequest(emailToVerify: String, templateId: String, continueUrl: String): Assertion = {
        val response = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify, templateId, continueUrl)))
        response.status shouldBe 201
        val token = decryptedToken(lastVerificationEmail)._1.get
        And("the client verifies the token")
        await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status shouldBe 201
        Then("the email should be verified")
        await(wsClient.url(appClient("/verified-email-check")).post(Json.obj("email" -> emailToVerify))).status shouldBe 200
      }

      Given("The email service is running")
      expectEmailToBeSent()

      When("client submits first verification request ")
      submitVerificationRequest("example1@domain.com", templateId, continueUrl)

      When("client submits second verification request ")
      submitVerificationRequest("example2@domain.com", templateId, continueUrl)
    }

    "return 502 error if email sending fails" in new Setup {
      val body = "some-5xx-message"
      expectEmailServiceToRespond(500, body)
      val response = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest()))
      response.status shouldBe 502
      response.body should include(body)
    }

    "return BAD_EMAIL_REQUEST error if email sending fails with 400" in new Setup {
      val body = "some-400-message"
      expectEmailServiceToRespond(400, body)
      val response = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest()))
      response.status shouldBe 400
      response.body should include(body)

      (Json.parse(response.body) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"
    }

    "return 500 error if email sending fails with 4xx" in new Setup {
      val body = "some-4xx-message"
      expectEmailServiceToRespond(404, body)
      val response = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest()))
      response.status shouldBe 502
      response.body should include(body)
    }

    "return 409 if email is already verified" in new Setup {
      assumeEmailAlreadyVerified(emailToVerify)

      val response = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify)))
      response.status shouldBe 409
      response.body shouldBe
        Json.parse(
          """{
            |"code":"EMAIL_VERIFIED_ALREADY",
            |"message":"Email has already been verified"
            |}""".stripMargin).toString()
    }
  }

  def assumeEmailAlreadyVerified(email: String): Assertion = {
    expectEmailToBeSent()
    await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(email))).status shouldBe 201
    val token = tokenFor(email)
    await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status shouldBe 201
  }

  trait Setup {
    val templateId = "my-lovely-template"
    val templateParams: Map[String, String] = Map("name" -> "Mr Joe Bloggs")
    val continueUrl = "http://some/url"

    val paramsJsonStr: String = Json.toJson(templateParams).toString()
    val expectedVerificationLink = "http://localhost:9890/verification?token=UG85NW1OcWdjR29xS29EM1pIQ1NqMlpzOEduemZCeUhvZVlLNUVtU2c3emp2TXZzRmFRSzlIdjJBTkFWVVFRUkg1M21MRUY4VE1TWDhOZ0hMNmQ0WHRQQy95NDZCditzNHd6ZUhpcEoyblNsT3F0bGJmNEw5RnhjOU0xNlQ3Y2o1dFdYVUE0NGFSUElURFRrSS9HRHhoTFZxdU9YRkw4OTZ4Z0tOTWMvQTJJd1ZqR3NJZ0pTNjRJNVRUc2RpcFZ1MjdOV1dhNUQ3OG9ITkVlSGJnaUJyUT09"
    val paramsWithVerificationLink: Map[String, String] = templateParams + ("verificationLink" -> expectedVerificationLink)
  }

}
