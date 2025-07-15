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

package uk.gov.hmrc.emailverification

import java.util.UUID
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mongodb.scala.result.InsertOneResult
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsDefined, JsString, JsValue, Json}
import play.api.libs.ws.WSResponse
import support.BaseISpec
import support.EmailStub._
import uk.gov.hmrc.emailverification.models.{English, Journey, PasscodeDoc}
import uk.gov.hmrc.emailverification.repositories.JourneyMongoRepository
import uk.gov.hmrc.http.HeaderNames

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class EmailPasscodeControllerISpec extends BaseISpec with Eventually {

  override def extraConfig: Map[String, Any] = super.extraConfig ++ Map("auditing.enabled" -> true)

  "testOnlyGetPasscode" should {
    "return a 200 with passcode if passcode exists in repository against sessionId" in new Setup {
      val passcode: String = generateUUID
      await(expectPasscodeToBePopulated(passcode))
      expectUserToBeAuthorisedWithGG(credId)

      val response: WSResponse = await(
        resourceRequest("/test-only/passcodes")
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .get()
      )
      response.status                                            shouldBe 200
      (Json.parse(response.body) \ "passcodes" \ 0 \ "passcode") shouldBe JsDefined(JsString(passcode))
    }

    "return passcodes from passcode and journey repositories" in new Setup {
      val passcode1: String = generateUUID
      val passcode2: String = generateUUID
      await(expectPasscodeToBePopulated(passcode1))
      expectUserToBeAuthorisedWithGG(credId)

      val journey: Journey = Journey(
        journeyId = UUID.randomUUID().toString,
        credId = credId,
        continueUrl = "/continueUrl",
        origin = "origin",
        accessibilityStatementUrl = "/accessibility",
        serviceName = "serviceName",
        language = English,
        emailAddress = Some(generateUUID),
        enterEmailUrl = Some("/enterEmail"),
        backUrl = Some("/back"),
        pageTitle = Some("title"),
        passcode = passcode2,
        emailAddressAttempts = 0,
        passcodesSentToEmail = 0,
        passcodeAttempts = 0,
        labels = None
      )

      expectJourneyToExist(journey)

      val response: WSResponse = await(
        resourceRequest("/test-only/passcodes")
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .get()
      )
      response.status                                            shouldBe 200
      (Json.parse(response.body) \ "passcodes" \ 0 \ "passcode") shouldBe JsDefined(JsString(passcode1))
      (Json.parse(response.body) \ "passcodes" \ 1 \ "passcode") shouldBe JsDefined(JsString(passcode2))
    }

    "return a 401 if a sessionId wasnt provided with the request" in new Setup {
      val response: WSResponse = await(resourceRequest("/test-only/passcodes").get())
      response.status                       shouldBe 401
      Json.parse(response.body) \ "code"    shouldBe JsDefined(JsString("NO_SESSION_ID"))
      Json.parse(response.body) \ "message" shouldBe JsDefined(JsString("No session id provided"))
    }

    "return a 404 if the session id is in the request but not in mongo" in new Setup {
      expectUserToBeAuthorisedWithGG(credId)
      val response: WSResponse = await(
        resourceRequest("/test-only/passcodes")
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .get()
      )
      response.status                       shouldBe 404
      Json.parse(response.body) \ "code"    shouldBe JsDefined(JsString("PASSCODE_NOT_FOUND"))
      Json.parse(response.body) \ "message" shouldBe JsDefined(JsString("No passcode found for sessionId"))
    }
  }

  "verifying an email" should {
    "return 400 if the lang is not en or cy" in new Setup {
      val response: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeRequest(emailToVerify, lang = "notenorcy"))
      )

      response.status shouldBe 400
    }

    "send a passcode email to the specified address successfully" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      Given("The email service is running")
      expectEmailToBeSent()

      When("a client submits a passcode email request")

      val response: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response.status shouldBe 201

      Then("a passcode email is sent")

      eventually {
        verifyEmailSentWithPasscode(emailToVerify)
        verifySendEmailWithPasscodeFired(ACCEPTED)
        verifyEmailPasscodeRequestSuccessfulEvent()
      }
    }

    "send a passcode email with the welsh template when the lang is cy" in new Setup {
      Given("The email service is running")
      expectEmailToBeSent()

      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      When("a client submits a passcode email request")

      val response: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify, lang = "cy"))
      )
      response.status shouldBe 201

      Then("a passcode email is sent")

      eventually {
        verifyEmailSentWithPasscode(emailToVerify, templateId = "email_verification_passcode_welsh")
        verifySendEmailWithPasscodeFired(ACCEPTED)
        verifyEmailPasscodeRequestSuccessfulEvent()
      }
    }

    "block user on 6th passcode request to the same email address" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      Given("The email service is running")
      expectEmailToBeSent()

      When("a client submits 5 requests to the same email address")

      (1 to 5).foreach { _ =>
        await(
          wsClient.url(appClient("/request-passcode")).withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123").post(passcodeRequest(emailToVerify))
        )
      }
      WireMock.reset()

      Then("client submits a 6th email request")
      expectUserToBeAuthorisedWithGG(credId)
      val response: WSResponse = await(
        wsClient.url(appClient("/request-passcode")).withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123").post(passcodeRequest(emailToVerify))
      )
      Then("the user should get 403 forbidden response")
      response.status shouldBe 403

      And("no email will have been sent")
      eventually {
        verifyNoEmailSent
      }
    }

    "block user on 11th different email passcode request" in new Setup {

      Given("The email service is running")
      expectEmailToBeSent()

      When("a client submits 10 different emails")

      (1 to 10).map { count =>
        expectUserToBeAuthorisedWithGG(credId)
        await(
          wsClient
            .url(appClient("/request-passcode"))
            .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
            .post(passcodeRequest(s"email$count@somewhere.com"))
        )
      }
      WireMock.reset()

      Then("client submits 11th email request")
      expectUserToBeAuthorisedWithGG(credId)
      val response: WSResponse = await(
        wsClient.url(appClient("/request-passcode")).withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123").post(passcodeRequest(emailToVerify))
      )
      Then("the user should get 403 forbidden response")
      response.status shouldBe 403

      And("no email will have been sent")
      eventually {
        verifyNoEmailSent
      }
    }

    "only latest passcode sent to a given email should be valid" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      Given("The email service is running")
      expectEmailToBeSent()

      When("client submits a passcode request")
      val response1: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response1.status shouldBe 201
      val passcode1: String = lastPasscodeEmailed

      When("client submits a second passcode request for same email")
      val response2: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response2.status shouldBe 201
      val passcode2: String = lastPasscodeEmailed

      Then("only the last passcode sent should be valid")
      val validationResponse1: WSResponse = await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeVerificationRequest(emailToVerify, passcode1))
      )

      val validationResponse2: WSResponse = await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeVerificationRequest(emailToVerify, passcode2))
      )

      validationResponse1.status shouldBe 404
      validationResponse2.status shouldBe 201

      verifySendEmailWithPasscodeFired(ACCEPTED)
      verifyEmailPasscodeRequestSuccessfulEvent(2)
    }

    "verifying an unknown passcode should return a 400 error" in new Setup {
      When("client submits an unknown passcode to verification request")
      val response: WSResponse = await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, "NDJRMS"))
      )
      response.status                                 shouldBe 404
      (Json.parse(response.body) \ "code").as[String] shouldBe "PASSCODE_NOT_FOUND"

      verifyEmailAddressNotFoundOrExpiredEventFired()
    }

    "return 404 Not Found and a PASSCODE_MISMATCH status if the passcode is incorrect" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      expectEmailToBeSent()

      val requestedPasscode: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      requestedPasscode.status shouldBe CREATED
      verifyEmailPasscodeRequestSuccessfulEvent()

      val response: WSResponse = await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(Json.obj("passcode" -> "DefinitelyWrong", "email" -> emailToVerify))
      )
      response.status shouldBe NOT_FOUND
      response.json   shouldBe Json.obj("code" -> "PASSCODE_MISMATCH", "message" -> "Passcode mismatch")

      verifyPasscodeMatchNotFoundOrExpiredEventFired()
    }

    "verifying a passcode with no sessionId should return a 401 unauthorized error" in new Setup {
      When("client submits a passcode to verification request without sessionId")
      val response: WSResponse = await(
        wsClient
          .url(appClient("/verify-passcode"))
          .post(passcodeVerificationRequest(emailToVerify, "NDJRMS"))
      )
      response.status                                 shouldBe 401
      (Json.parse(response.body) \ "code").as[String] shouldBe "NO_SESSION_ID"

      verifyVerificationRequestMissingSessionIdEventFired()
    }

    "fail with Forbidden on exceeding max permitted passcode verification attempts (default is 5)" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      Given("The email service is running")
      expectEmailToBeSent()

      When("client submits a passcode email request")
      val response1: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response1.status shouldBe 201
      verifyEmailPasscodeRequestSuccessfulEvent()

      And("submits an unknown passcode for verification 5 times")

      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, "NDJRMS"))
      ).status shouldBe 404
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, "NDJRMS"))
      ).status shouldBe 404
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, "NDJRMS"))
      ).status shouldBe 404
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, "NDJRMS"))
      ).status shouldBe 404
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, "NDJRMS"))
      ).status shouldBe 404

      Then("the next verification attempt is blocked with forbidden response, even though correct passcode is being used")
      val response6: WSResponse = await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, lastPasscodeEmailed))
      )

      response6.status                                 shouldBe 403
      (Json.parse(response6.body) \ "code").as[String] shouldBe "MAX_PASSCODE_ATTEMPTS_EXCEEDED"

      And("email is not verified event fired 5 times only as 6th was blocked")
      verifyPasscodeMatchNotFoundOrExpiredEventFired(5)
      verifyMaxPasscodeAttemptsExceededEventFired()
    }

    "lowercase email address" in new Setup {
      Given("The email service is running")
      expectEmailToBeSent()

      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      When("client requests a passcode")
      val response1: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify.toUpperCase))
      )
      response1.status shouldBe 201
      verifyEmailPasscodeRequestSuccessfulEvent()

      Then("submitting verification request with passcode in lowercase should be successful")
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeVerificationRequest(emailToVerify, lastPasscodeEmailed))
      ).status shouldBe 201

      verifyEmailAddressConfirmedEventFired()
    }

    "uppercase passcode verification is valid" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      Given("The email service is running")
      expectEmailToBeSent()

      When("client requests a passcode")
      val response1: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response1.status shouldBe 201
      verifyEmailPasscodeRequestSuccessfulEvent()
      val uppercasePasscode: String = lastPasscodeEmailed.toUpperCase

      Then("submitting verification request with passcode in lowercase should be successful")
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, uppercasePasscode))
      ).status shouldBe 201

      verifyEmailAddressConfirmedEventFired()
    }

    "lowercase passcode verification is valid" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      Given("The email service is running")
      expectEmailToBeSent()

      When("client requests a passcode")
      val response1: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response1.status shouldBe 201
      verifyEmailPasscodeRequestSuccessfulEvent()
      val lowercasePasscode: String = lastPasscodeEmailed.toLowerCase

      Then("submitting verification request with passcode in lowercase should be successful")
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, lowercasePasscode))
      ).status shouldBe 201

      verifyEmailAddressConfirmedEventFired()
    }

    "second passcode verification request with same session and passcode should return 204 instead of 201 response" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      Given("The email service is running")
      expectEmailToBeSent()

      When("client submits a passcode verification request")
      val response1: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response1.status shouldBe 201
      verifyEmailPasscodeRequestSuccessfulEvent()
      val passcode: String = lastPasscodeEmailed

      Then("the request to verify with passcode should be successful")
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, passcode))
      ).status shouldBe 201
      Then("an additional request to verify the same passcode should be successful, but return with a 204 response")
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, passcode))
      ).status shouldBe 204

      verifyEmailAddressConfirmedEventFired(2)
    }

    "passcode verification for two different emails on same session should be successful" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      Given("The email service is running")
      expectEmailToBeSent()

      When("user sends two passcode requests for two different email addresses")
      val email1 = "example1@domain.com"
      val response1: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(email1))
      )
      response1.status shouldBe 201
      val passcode1: String = lastPasscodeEmailed

      val email2 = "example2@domain.com"
      val response2: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(email2))
      )
      response2.status shouldBe 201
      val passcode2: String = lastPasscodeEmailed

      verifyEmailPasscodeRequestSuccessfulEvent(2)

      Then("both passcodes can be verified")
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(email1, passcode1))
      ).status shouldBe 201

      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(email2, passcode2))
      ).status shouldBe 201

      verifyEmailAddressConfirmedEventFired(2)
    }

    "return 502 error if email sending fails" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      val body = "some-5xx-message"
      expectEmailServiceToRespond(500, body)
      val response: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response.status shouldBe 502
      response.body     should include(body)

      verifySendEmailWithPasscodeFired(500)
      verifyPasscodeEmailDeliveryErrorEventFired()
    }

    "return BAD_EMAIL_REQUEST error if email sending fails with 400" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      val body = "some-400-message"
      expectEmailServiceToRespond(400, body)
      val response: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response.status shouldBe 400
      response.body     should include(body)

      (Json.parse(response.body) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"

      verifySendEmailWithPasscodeFired(400)
      verifyPasscodeEmailDeliveryErrorEventFired()
    }

    "return 500 error if email sending fails with 4xx" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      val body = "some-4xx-message"
      expectEmailServiceToRespond(404, body)
      val response: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response.status shouldBe 502
      response.body     should include(body)

      verifySendEmailWithPasscodeFired(404)
      verifyPasscodeEmailDeliveryErrorEventFired()
    }

    "return 401 error if no sessionID is provided" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      val body = "some-4xx-message"
      expectEmailServiceToRespond(404, body)
      val response: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders("Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response.status                       shouldBe 401
      Json.parse(response.body) \ "code"    shouldBe JsDefined(JsString("NO_SESSION_ID"))
      Json.parse(response.body) \ "message" shouldBe JsDefined(JsString("No session id provided"))
      verifyEmailRequestMissingSessionIdEventFired()
    }

    "return 409 if email is already verified" in new Setup {
      Given("The user is logged in")
      expectUserToBeAuthorisedWithGG(credId)

      assumeEmailAlreadyVerified()

      val response: WSResponse = await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      )
      response.status shouldBe 409

      val json: JsValue = Json.parse(response.body)
      (json \ "code").as[String]    shouldBe "EMAIL_VERIFIED_ALREADY"
      (json \ "message").as[String] shouldBe "Email has already been verified"

      verifyEmailAddressAlreadyVerifiedEventFired()
    }
  }

  trait Setup {
    val templateId = "my-lovely-template"
    val emailToVerify = "example@domain.com"
    val sessionId: String = generateUUID
    val credId: String = generateUUID

    def assumeEmailAlreadyVerified(): Assertion = {
      expectEmailToBeSent()
      await(
        wsClient
          .url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId, "Authorization" -> "Bearer123")
          .post(passcodeRequest(emailToVerify))
      ).status shouldBe 201
      val passcode = lastPasscodeEmailed
      await(
        wsClient
          .url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(emailToVerify, passcode))
      ).status shouldBe 201
    }

    def expectPasscodeToBePopulated(passcode: String): Future[InsertOneResult] = {
      val expiresAt = Instant.now.plus(2, ChronoUnit.HOURS)
      val email = generateUUID

      passcodeRepo.collection.insertOne(PasscodeDoc(sessionId, email, passcode, expiresAt, 0, 1)).toFuture()
    }

    def generateUUID: String = UUID.randomUUID().toString

    def expectUserToBeAuthorisedWithGG(credId: String): Unit = {
      stubFor(
        post("/auth/authorise")
          .willReturn(
            okJson(
              Json
                .obj(
                  "optionalCredentials" -> Json.obj(
                    "providerId"   -> credId,
                    "providerType" -> "GG"
                  )
                )
                .toString()
            )
          )
      )
    }

    def expectJourneyToExist(journey: Journey): Unit = {
      await(
        journeyRepo.collection
          .insertOne(
            JourneyMongoRepository.Entity(
              journeyId = journey.journeyId,
              credId = journey.credId,
              continueUrl = journey.continueUrl,
              origin = journey.origin,
              accessibilityStatementUrl = journey.accessibilityStatementUrl,
              serviceName = journey.serviceName,
              language = journey.language,
              emailAddress = journey.emailAddress,
              enterEmailUrl = journey.enterEmailUrl,
              backUrl = journey.backUrl,
              pageTitle = journey.pageTitle,
              passcode = journey.passcode,
              createdAt = Instant.now(),
              emailAddressAttempts = journey.emailAddressAttempts,
              passcodesSentToEmail = journey.passcodesSentToEmail,
              passcodeAttempts = journey.passcodeAttempts,
              labels = journey.labels
            )
          )
          .toFuture()
      )
    }

    def verifySendEmailWithPasscodeFired(responseCode: Int): Unit = {
      eventually {
        verify(
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"SendEmailWithPasscode""""))
            .withRequestBody(containing(s""""responseCode":"$responseCode""""))
        )
      }
    }

    def verifyCheckEmailVerifiedFired(emailVerified: Boolean, times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"CheckEmailVerified""""))
            .withRequestBody(containing(s""""emailVerified":"$emailVerified""""))
        )
      }
    }

    def verifyEmailPasscodeRequestSuccessfulEvent(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationRequest""""))
            .withRequestBody(containing(s""""outcome":"Successfully sent a passcode to the email address requiring verification""""))
        )
      }
    }

    def verifyEmailAddressAlreadyVerifiedEventFired(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationRequest""""))
            .withRequestBody(containing(s""""outcome":"Email address already verified""""))
        )
      }
    }

    def verifyMaxEmailsExceededEventFired(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationRequest""""))
            .withRequestBody(containing(s""""outcome":"Max permitted passcode emails per session has been exceeded""""))
        )
      }
    }

    def verifyMaxDifferentEmailsExceededEventFired(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationRequest""""))
            .withRequestBody(containing(s""""outcome":"Max permitted passcode emails per session has been exceeded""""))
        )
      }
    }

    def verifyPasscodeEmailDeliveryErrorEventFired(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationRequest""""))
            .withRequestBody(containing(s""""outcome":"sendEmail request failed""""))
        )
      }
    }

    def verifyEmailAddressConfirmedEventFired(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationResponse""""))
            .withRequestBody(containing(s""""outcome":"Email address confirmed""""))
        )
      }
    }

    def verifyEmailAddressNotFoundOrExpiredEventFired(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationResponse""""))
            .withRequestBody(containing(s""""outcome":"Email address not found or verification attempt time expired""""))
        )
      }
    }

    def verifyPasscodeMatchNotFoundOrExpiredEventFired(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationResponse""""))
            .withRequestBody(containing(s""""outcome":"Email verification passcode match not found or time expired""""))
        )
      }
    }

    def verifyVerificationRequestMissingSessionIdEventFired(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationResponse""""))
            .withRequestBody(containing(s""""outcome":"SessionId missing""""))
        )
      }
    }

    def verifyEmailRequestMissingSessionIdEventFired(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationRequest""""))
            .withRequestBody(containing(s""""outcome":"SessionId missing""""))
        )
      }
    }

    def verifyMaxPasscodeAttemptsExceededEventFired(times: Int = 1): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"PasscodeVerificationResponse""""))
            .withRequestBody(containing(s""""outcome":"Max permitted passcode verification attempts have been exceeded""""))
        )
      }
    }

  }

}
