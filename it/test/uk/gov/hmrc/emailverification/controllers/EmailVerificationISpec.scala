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
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.DefaultBodyReadables.readableAsString
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.EmailStub.{decryptedToken, expectEmailServiceToRespond, expectEmailToBeSent, lastVerificationEmail, verificationRequest, verifyEmailSentWithContinueUrl}
import support.IntegrationBaseSpec
import uk.gov.hmrc.emailverification.models.Journey

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}
import play.api.libs.ws.writeableOf_JsValue

class EmailVerificationISpec extends IntegrationBaseSpec with ScalaFutures {

  implicit lazy val config: Config = Configuration.from(serviceConfig).underlying

  val emailToVerify = "example@domain.com"

  "email verification" should {
    val continueUrls = Table[String, String, String](
      ("emailToVerify", "templateId", "continueUrl"),
      (emailToVerify, "dd_email_verifcation", "https://www.tax.service.gov.uk/direct-debit/email-success"),
      (emailToVerify, "register_your_company_verification_email", "https://www.tax.service.gov.uk/register-your-company/post-sign-in"),
      (emailToVerify, "verifyEmailAddress", "https://www.tax.service.gov.uk/manage-email-cds/email-address-confirmed"),
      (emailToVerify,
       "cgtpd_email_verification",
       s"https://www.tax.service.gov.uk/capital-gains-tax-uk-property/subscribed/amend-details/verify-email?p=${UUID.randomUUID().toString}"
      ),
      (emailToVerify, "cgtpd_email_verification", s"https://www.tax.service.gov.uk/capital-gains-tax-uk-property/about-person/verify-email?p=${UUID.randomUUID().toString}"),
      (emailToVerify, "cgtpd_email_verification", s"https://www.tax.service.gov.uk/capital-gains-tax-uk-property/registration/verify-email?p=${UUID.randomUUID().toString}"),
      (emailToVerify,
       "cgtpd_email_verification",
       s"https://www.tax.service.gov.uk/capital-gains-tax-uk-property/registration/amend-details/verify-email?p=${UUID.randomUUID().toString}"
      ),
      (emailToVerify,
       "cgtpd_email_verification",
       s"https://www.tax.service.gov.uk/capital-gains-tax-uk-property/subscription/amend-details/verify-email?p=${UUID.randomUUID().toString}"
      ),
      (emailToVerify, "cgtpd_email_verification", s"https://www.tax.service.gov.uk/capital-gains-tax-uk-property/subscription/verify-email?p=${UUID.randomUUID().toString}"),
      (emailToVerify, "hts_verification_email", s"https://www.tax.service.gov.uk/help-to-save/email-confirmed-callback?p=${UUID.randomUUID().toString}"),
      (emailToVerify, "hts_verification_email", s"https://www.tax.service.gov.uk/help-to-save/account-home/email-confirmed-callback?p=${UUID.randomUUID().toString}"),
      (emailToVerify, "fhdds_email_verification", s"https://www.tax.service.gov.uk/fhdds/email-verify/${emailToVerify.hashCode.toHexString.toUpperCase}")
    )

    forAll(continueUrls) { (email, template, url) =>
      s"send the verification email to the specified address successfully with continue url $url" in new Setup {
        expectEmailToBeSent()

        val response: WSResponse = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(email, template, url)))
        response.status shouldBe 201

        verifyEmailSentWithContinueUrl(email, url, template)
      }

      s"only latest email verification request token with continue url $url for a given email should be valid" in new Setup {
        expectEmailToBeSent()

        val response1: WSResponse = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(email, template, url)))
        response1.status shouldBe 201
        val token1: String = decryptedToken(lastVerificationEmail)._1.get

        val response2: WSResponse = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(email, template, url)))
        response2.status shouldBe 201
        val token2: String = decryptedToken(lastVerificationEmail)._1.get

        await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token1))).status shouldBe 400
        await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token2))).status shouldBe 201
      }

      s"second verification request with continue url $url should return successful 204 response" in new Setup {
        expectEmailToBeSent()

        val response1: WSResponse = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(email, template, url)))
        response1.status shouldBe 201
        val token: String = decryptedToken(lastVerificationEmail)._1.get

        await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status shouldBe 201
        await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status shouldBe 204
      }

      s"email verification for two different emails with continue url $url should be successful" in new Setup {
        def submitVerificationRequest(emailToVerify: String, templateId: String, continueUrl: String): Assertion = {
          val response = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify, templateId, continueUrl)))
          response.status shouldBe 201
          val token = decryptedToken(lastVerificationEmail)._1.get
          await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status     shouldBe 201
          await(wsClient.url(appClient("/verified-email-check")).post(Json.obj("email" -> emailToVerify))).status shouldBe 200
        }

        expectEmailToBeSent()

        submitVerificationRequest("example1@domain.com", template, url)
        submitVerificationRequest("example2@domain.com", template, url)
      }
    }

    "return 502 error if email sending fails" in new Setup {
      val body = "some-5xx-message"
      expectEmailServiceToRespond(500, body)
      val response: WSResponse = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest()))
      response.status shouldBe 502
      response.body     should include(body)
    }

    "return BAD_EMAIL_REQUEST error if email sending fails with 400" in new Setup {
      val body = "some-400-message"
      expectEmailServiceToRespond(400, body)
      val response: WSResponse = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest()))
      response.status shouldBe 400
      response.body     should include(body)

      (Json.parse(response.body) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"
    }

    "return 500 error if email sending fails with 4xx" in new Setup {
      val body = "some-4xx-message"
      expectEmailServiceToRespond(404, body)
      val response: WSResponse = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest()))
      response.status shouldBe 502
      response.body     should include(body)
    }

    "return 409 if email is already verified" in new Setup {
      assumeEmailAlreadyVerified(emailToVerify)

      val response: WSResponse = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify)))
      response.status shouldBe 409
      response.body shouldBe
        Json
          .parse("""{
              |"code":"EMAIL_VERIFIED_ALREADY",
              |"message":"Email has already been verified"
              |}""".stripMargin)
          .toString()
    }

    "submit multiple emails should increase the email attempts count" in new Setup {
      // Given("The email service is running")
      expectEmailToBeSent()

      // When("client submits two verify email requests with diff email")
      val response: WSResponse = await(wsClient.url(appClient("/verify-email")).post(verifyEmailRequestJson(emailAddress)))
      response.status shouldBe 201
      val response1: WSResponse = await(wsClient.url(appClient("/verify-email")).post(verifyEmailRequestJson(emailAddress1)))
      response1.status shouldBe 201
      val response2: WSResponse = await(wsClient.url(appClient("/verify-email")).post(verifyEmailRequestJson(emailAddress2)))
      response2.status shouldBe 201
      val response3: WSResponse = await(wsClient.url(appClient("/verify-email")).post(verifyEmailRequestJson(emailAddress3)))
      response3.status shouldBe 201
      val response4: WSResponse = await(wsClient.url(appClient("/verify-email")).post(verifyEmailRequestJson(emailAddress4)))
      response4.status shouldBe 201
      val response5: WSResponse = await(wsClient.url(appClient("/verify-email")).post(verifyEmailRequestJson(emailAddress5)))
      response5.status shouldBe 401 // 6th fails as max different emails limit is 5
      val eventualJourneys: Future[Seq[Journey]] = journeyRepo.findByCredId(credId)
      val journeyId4: String = await(eventualJourneys).find(_.emailAddress.contains(emailAddress4)).map(_.journeyId).getOrElse("not-found")
      val submitEmailResponse: WSResponse = await(wsClient.url(appClient(s"/journey/$journeyId4/email")).post(Json.obj("email" -> emailAddress1)))
      submitEmailResponse.status shouldBe 403

      // Then("verify the email retry count is incremented and status is locked")
      await(verificationStatusRepo.isLocked(credId, emailAddress1)) shouldBe true
    }
  }

  def assumeEmailAlreadyVerified(email: String): Assertion = {
    expectEmailToBeSent()
    await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(email))).status shouldBe 201
    val token = tokenFor(email)
    await(wsClient.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status shouldBe 201
  }

  def tokenFor(email: String): String = {
    expectEmailToBeSent()

    await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify = email))).status shouldBe 201
    decryptedToken(lastVerificationEmail)._1.get
  }

  trait Setup {
    val templateId = "my-lovely-template"
    val templateParams: Map[String, String] = Map("name" -> "Mr Joe Bloggs")
    val continueUrl = "http://some/url"

    val paramsJsonStr: String = Json.toJson(templateParams).toString()
    val expectedVerificationLink =
      "http://localhost:9890/verification?token=UG85NW1OcWdjR29xS29EM1pIQ1NqMlpzOEduemZCeUhvZVlLNUVtU2c3emp2TXZzRmFRSzlIdjJBTkFWVVFRUkg1M21MRUY4VE1TWDhOZ0hMNmQ0WHRQQy95NDZCditzNHd6ZUhpcEoyblNsT3F0bGJmNEw5RnhjOU0xNlQ3Y2o1dFdYVUE0NGFSUElURFRrSS9HRHhoTFZxdU9YRkw4OTZ4Z0tOTWMvQTJJd1ZqR3NJZ0pTNjRJNVRUc2RpcFZ1MjdOV1dhNUQ3OG9ITkVlSGJnaUJyUT09"
    val paramsWithVerificationLink: Map[String, String] = templateParams + ("verificationLink" -> expectedVerificationLink)

    val passcode = "FGTRWX"
    val credId: String = UUID.randomUUID().toString
    val origin = "ppt"
    val emailAddress = "barrywood@hotmail.com"
    val emailAddress1 = "barrywood1@hotmail.com"
    val emailAddress2 = "barrywood2@hotmail.com"
    val emailAddress3 = "barrywood3@hotmail.com"
    val emailAddress4 = "barrywood4@hotmail.com"
    val emailAddress5 = "barrywood5@hotmail.com"
    val emailAddress6 = "barrywood6@hotmail.com"

    val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

    def verifyEmailRequestJson(emailAddr: String): JsValue = {
      Json.parse(s"""{
           |  "credId": "$credId",
           |  "continueUrl": "$continueUrl",
           |  "origin" : "$origin",
           |  "deskproServiceName" : "",
           |  "accessibilityStatementUrl": "/",
           |  "continueUrl": "$continueUrl",
           |  "email" : { "address" : "$emailAddr", "enterUrl"  : "/ppt/email" },
           |  "lang" : "en",
           |  "useNewGovUkServiceNavigation" : true,
           |  "backUrl" : "",
           |  "pageTitle" : ""
           |}""".stripMargin)
    }
  }

}
