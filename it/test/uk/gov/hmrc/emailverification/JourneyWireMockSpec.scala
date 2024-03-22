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
import support.BaseISpec
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.mongodb.scala.result.InsertOneResult
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsArray, JsNull, JsObject, Json}
import play.api.libs.ws.WSResponse
import play.api.test.Injecting
import uk.gov.hmrc.emailverification.models.{English, Journey, VerificationStatus}
import uk.gov.hmrc.emailverification.repositories.{JourneyMongoRepository, VerificationStatusMongoRepository}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class JourneyWireMockSpec extends BaseISpec with Injecting {

  override def extraConfig: Map[String, Any] = super.extraConfig ++ Map("auditing.enabled" -> true)

  "POST /verify-email" when {
    "given a valid payload and the email was successfully sent out" should {
      "return a redirect url to the journey endpoint on the frontend" in new Setup {
        expectEmailToSendSuccessfully()
        val response: WSResponse = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload()))

        val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

        response.status                          shouldBe CREATED
        (response.json \ "redirectUri").as[String] should fullyMatch regex s"/email-verification/journey/$uuidRegex/passcode\\?continueUrl=$continueUrl&origin=$origin"
        verifyEmailRequestEventFired(1, emailAddress, CREATED)
      }

      "lower case email address" in new Setup {
        expectEmailToSendSuccessfully()
        val response: WSResponse = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload(emailAddress.toUpperCase)))

        val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

        response.status                          shouldBe CREATED
        (response.json \ "redirectUri").as[String] should fullyMatch regex s"/email-verification/journey/$uuidRegex/passcode\\?continueUrl=$continueUrl&origin=$origin"
        verifyEmailRequestEventFired(1, emailAddress, CREATED)
      }

      "the email should contain the English service name when present" in new Setup {
        expectEmailToSendSuccessfully()
        val labels: JsObject = Json.obj(
          "cy" -> Json.obj(
            "pageTitle"             -> JsNull,
            "userFacingServiceName" -> JsNull
          ),
          "en" -> Json.obj(
            "pageTitle"             -> "Page Title.en",
            "userFacingServiceName" -> "Team Name"
          )
        )
        val response: WSResponse = await(resourceRequest("/email-verification/verify-email").post(verifyEmailWithLabelsPayload(emailAddress, labels)))

        val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

        response.status                          shouldBe CREATED
        (response.json \ "redirectUri").as[String] should fullyMatch regex s"/email-verification/journey/$uuidRegex/passcode\\?continueUrl=$continueUrl&origin=$origin"
        verifyEmailRequestEventFired(1, emailAddress, CREATED)
        eventually {
          verify(1,
                 postRequestedFor(urlEqualTo("/hmrc/email"))
                   .withRequestBody(containing(s""""team_name":"Team Name""""))
                )
        }
      }

      "the email should contain the Welsh service name when all fields are present" in new Setup {
        expectEmailToSendSuccessfully()
        val labels: JsObject = Json.obj(
          "cy" -> Json.obj(
            "pageTitle"             -> "Page Title.cy",
            "userFacingServiceName" -> "Team Name.cy"
          ),
          "en" -> Json.obj(
            "pageTitle"             -> "Page Title.en",
            "userFacingServiceName" -> "Team Name.en"
          )
        )
        val response: WSResponse = await(resourceRequest("/email-verification/verify-email").post(verifyEmailWithLabelsPayload(emailAddress, labels)))

        val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

        response.status                          shouldBe CREATED
        (response.json \ "redirectUri").as[String] should fullyMatch regex s"/email-verification/journey/$uuidRegex/passcode\\?continueUrl=$continueUrl&origin=$origin"
        verifyEmailRequestEventFired(1, emailAddress, CREATED)
        eventually {
          verify(1,
                 postRequestedFor(urlEqualTo("/hmrc/email"))
                   .withRequestBody(containing(s""""team_name":"Team Name.cy""""))
                )
        }
      }
    }

    "given that there is already 4 different email submissions, after submitting a new different email address, the next different email address locks the user out" in new Setup {
      expectEmailToSendSuccessfully()

      val testEmail1 = "aaa1@bbb.ccc"
      val testEmail2 = "aaa2@bbb.ccc"
      val testEmail3 = "aaa3@bbb.ccc"
      val testEmail4 = "aaa4@bbb.ccc"
      val testEmail5 = "aaa5@bbb.ccc"
      val testEmail6 = "aaa6@bbb.ccc"

      val journey: Journey = Journey(
        UUID.randomUUID().toString,
        credId,
        "/continueUrl",
        "origin",
        "/accessibility",
        "serviceName",
        English,
        None,
        None,
        None,
        None,
        "passcode",
        1,
        0,
        0
      )

      expectJourneyToExist(journey.copy(journeyId = UUID.randomUUID().toString, emailAddress = Some(testEmail1)))
      expectJourneyToExist(journey.copy(journeyId = UUID.randomUUID().toString, emailAddress = Some(testEmail2)))
      expectJourneyToExist(journey.copy(journeyId = UUID.randomUUID().toString, emailAddress = Some(testEmail3)))
      expectJourneyToExist(journey.copy(journeyId = UUID.randomUUID().toString, emailAddress = Some(testEmail4)))

      val firstResponse: WSResponse = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload(testEmail5)))
      val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

      (firstResponse.json \ "redirectUri").as[String] should fullyMatch regex s"/email-verification/journey/$uuidRegex/passcode\\?continueUrl=$continueUrl&origin=$origin"
      verifyEmailRequestEventFired(1, testEmail5, CREATED)

      val secondResponse: WSResponse = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload(testEmail6)))

      secondResponse.status shouldBe UNAUTHORIZED
    }

    "given a valid payload but the email was previously locked out" should {
      "return unauthorised response" in new Setup {
        val emailsToBeStored: List[VerificationStatus] = List(
          VerificationStatus("email1", verified = true, locked = false),
          VerificationStatus("email2", verified = false, locked = true)
        )

        expectEmailsToBeStored(emailsToBeStored)
        val response: WSResponse = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload("email2")))
        response.status shouldBe UNAUTHORIZED
        verifyEmailRequestEventFired(1, "email2", UNAUTHORIZED)
      }
    }

    "given a valid payload and the email failed to send" should {
      "return a redirect url to the journey endpoint on the frontend" in new Setup {
        expectEmailToFailToSend()

        val response: WSResponse = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload()))

        response.status shouldBe BAD_GATEWAY
        verifyEmailRequestEventFired(1, emailAddress, BAD_GATEWAY)
      }
    }
    "given no email in valid payload" should {
      "show the submit email page as continueUrl" in new Setup {
        val response: WSResponse = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload() - "email"))

        val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

        response.status                          shouldBe CREATED
        (response.json \ "redirectUri").as[String] should fullyMatch regex s"/email-verification/journey/$uuidRegex/email\\?continueUrl=$continueUrl&origin=$origin"
      }
    }
  }

  "GET /journey/:journeyId" should {
    "return 200 OK and the frontend journey data when the journey ID is valid" in new Setup {
      val journey: Journey = Journey(
        UUID.randomUUID().toString,
        "credId",
        "/continueUrl",
        "origin",
        "/accessibility",
        "serviceName",
        English,
        None,
        Some("/enterEmail"),
        Some("/back"),
        Some("title"),
        "passcode",
        0,
        0,
        0
      )

      expectJourneyToExist(journey)

      val result: WSResponse = await(resourceRequest(s"/email-verification/journey/${journey.journeyId}").get())
      result.status shouldBe OK
      result.json shouldBe Json.obj(
        "accessibilityStatementUrl" -> "/accessibility",
        "enterEmailUrl"             -> "/enterEmail",
        "deskproServiceName"        -> "serviceName",
        "backUrl"                   -> "/back",
        "serviceTitle"              -> "title"
      )
    }

    "return 404 Not Found when the journey ID is invalid" in new Setup {
      val result: WSResponse = await(resourceRequest("/email-verification/journey/nope").get())
      result.status shouldBe NOT_FOUND
    }
  }

  "POST /journey/:journeyId/email" when {
    "the journey ID is valid and more attempts are allowed" should {
      "send the passcode email and return 200 OK" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          None,
          None,
          None,
          None,
          "passcode",
          0,
          0,
          0
        )

        expectJourneyToExist(journey)
        expectPasscodeEmailToBeSent(journey.passcode)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/email")
            .post(Json.obj("email" -> "aaa@bbb.ccc"))
        )
        result.status shouldBe OK
        result.json   shouldBe Json.obj("status" -> "accepted")
      }

      "given that there is already 4 email passcode submissions to the same email, after making another same email passcode submission, the next same email passcode submission is locked out" in new Setup {

        val testEmail = "aaa@bbb.ccc"

        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some(testEmail),
          Some("/enterEmail"),
          Some("/back"),
          Some("title"),
          "passcode",
          0,
          4,
          0
        )

        expectJourneyToExist(journey.copy(passcodesSentToEmail = 4))
        expectPasscodeEmailToBeSent(journey.passcode)

        val firstResponse: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/email")
            .post(Json.obj("email" -> testEmail))
        )
        firstResponse.status shouldBe OK
        firstResponse.json   shouldBe Json.obj("status" -> "accepted")

        val secondResponse: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/email")
            .post(Json.obj("email" -> testEmail))
        )
        secondResponse.status shouldBe FORBIDDEN
        secondResponse.json   shouldBe Json.obj("status" -> "tooManyAttempts", "continueUrl" -> "/continueUrl")
      }
    }

    "the journey ID is invalid" should {
      "return 404 Not Found" in new Setup {
        val result: WSResponse = await(
          resourceRequest("/email-verification/journey/nope/email")
            .post(Json.obj("email" -> "aaa@bbb.ccc"))
        )

        result.status shouldBe NOT_FOUND
        result.json   shouldBe Json.obj("status" -> "journeyNotFound")
      }
    }

    "the journey ID is valid but too many attempts have been made" should {
      "return 403 Forbidden and the continue URL" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          None,
          None,
          None,
          None,
          "passcode",
          emailAddressAttempts = 100,
          0,
          0
        )

        expectJourneyToExist(journey)
        expectPasscodeEmailToBeSent(journey.passcode)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/email")
            .post(Json.obj("email" -> "aaa@bbb.ccc"))
        )
        result.status shouldBe FORBIDDEN
        result.json shouldBe Json.obj(
          "status"      -> "tooManyAttempts",
          "continueUrl" -> "/continueUrl"
        )
      }
    }
  }

  "POST /journey/:journeyId/resend-passcode" when {
    "the journey ID is valid and more attempts are allowed in the session" should {
      "resend the passcode email and return 200 OK" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa@bb.cc"),
          Some("/enterEmailUrl"),
          None,
          None,
          "passcode",
          0,
          0,
          0
        )

        expectJourneyToExist(journey)
        expectPasscodeEmailToBeSent(journey.passcode)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/resend-passcode")
            .post(Json.obj())
        )
        result.status shouldBe OK
        result.json   shouldBe Json.obj("status" -> "passcodeResent")
      }

      "given 4 failed passcode resends to the same email, then a subsequent passcode resend, the next passcode resend submission should lock the user out" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa@bb.cc"),
          Some("/enterEmailUrl"),
          None,
          None,
          "passcode",
          0,
          4,
          0
        )

        expectJourneyToExist(journey)
        expectPasscodeEmailToBeSent(journey.passcode)

        val firstResponse: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/resend-passcode")
            .post(Json.obj())
        )
        firstResponse.status shouldBe OK
        firstResponse.json   shouldBe Json.obj("status" -> "passcodeResent")

        val secondResponse: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/resend-passcode")
            .post(Json.obj())
        )
        secondResponse.status shouldBe FORBIDDEN
        secondResponse.json shouldBe Json.obj(
          "status" -> "tooManyAttemptsForEmail",
          "journey" -> Json.obj("accessibilityStatementUrl" -> "/accessibility",
                                "deskproServiceName" -> "serviceName",
                                "enterEmailUrl"      -> "/enterEmailUrl",
                                "emailAddress"       -> "aa@bb.cc"
                               )
        )
      }
    }

    "the journey ID is invalid" should {
      "return 404 Not Found" in new Setup {
        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/nope/resend-passcode")
            .post(Json.obj())
        )
        result.status shouldBe NOT_FOUND
      }
    }

    "the journey ID is valid but too many attempts have been made for the current email address" should {
      "return 403 Forbidden and the frontend journey data" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa@bb.cc"),
          Some("/enterEmailUrl"),
          Some("/back"),
          Some("title"),
          "passcode",
          0,
          passcodesSentToEmail = 100,
          passcodeAttempts = 0
        )

        expectJourneyToExist(journey)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/resend-passcode")
            .post(Json.obj())
        )
        result.status shouldBe FORBIDDEN
        result.json shouldBe Json.obj(
          "status" -> "tooManyAttemptsForEmail",
          "journey" -> Json.obj(
            "accessibilityStatementUrl" -> "/accessibility",
            "enterEmailUrl"             -> "/enterEmailUrl",
            "deskproServiceName"        -> "serviceName",
            "backUrl"                   -> "/back",
            "serviceTitle"              -> "title",
            "emailAddress"              -> "aa@bb.cc"
          )
        )
      }
    }

    "the journey ID is valid but too many attempts have been made in the session" should {
      "return 403 Forbidden and the continue URL" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa@bb.cc"),
          Some("/enterEmailUrl"),
          None,
          None,
          "passcode",
          0,
          passcodesSentToEmail = 0,
          passcodeAttempts = 100
        )

        expectJourneyToExist(journey)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/resend-passcode")
            .post(Json.obj())
        )
        result.status shouldBe FORBIDDEN
        result.json   shouldBe Json.obj("status" -> "tooManyAttemptsInSession", "continueUrl" -> "/continueUrl")
      }
    }
  }

  "POST /journey/:journeyId/passcode" when {
    "the journey ID is valid and the passcode is correct" should {
      "verify the email address and return 200 OK and the continue URL" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa@bb.cc"),
          Some("/enterEmailUrl"),
          None,
          None,
          "passcode",
          0,
          0,
          0
        )

        expectJourneyToExist(journey)
        expectEmailsToBeStored(List(VerificationStatus("aa@bb.cc", verified = false, locked = false)))
        expectUserToBeAuthorisedWithGG(credId)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
            .withHttpHeaders("Authorization" -> "Bearer123")
            .post(Json.obj("passcode" -> "passcode"))
        )
        result.status shouldBe OK
        result.json   shouldBe Json.obj("status" -> "complete", "continueUrl" -> "/continueUrl")

        val verified: WSResponse = await(resourceRequest(s"/email-verification/verification-status/$credId").get())
        verified.status shouldBe OK
        verified.json shouldBe Json.obj(
          "emails" -> Json.arr(
            Json.obj(
              "emailAddress" -> "aa@bb.cc",
              "verified"     -> true,
              "locked"       -> false
            )
          )
        )
      }
      "a passcode is submitted correctly after a previous fail returns a 200" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa@bb.cc"),
          Some("/enterEmailUrl"),
          Some("/back"),
          Some("title"),
          "passcode",
          0,
          0,
          0
        )

        expectJourneyToExist(journey)
        expectEmailsToBeStored(List(VerificationStatus("aa@bb.cc", verified = false, locked = false)))
        expectUserToBeAuthorisedWithGG(credId)

        val firstResponse: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
            .withHttpHeaders("Authorization" -> "Bearer123")
            .post(Json.obj("passcode" -> "not the right passcode"))
        )
        firstResponse.status shouldBe BAD_REQUEST
        firstResponse.json shouldBe Json.obj(
          "status" -> "incorrectPasscode",
          "journey" -> Json.obj(
            "accessibilityStatementUrl" -> "/accessibility",
            "enterEmailUrl"             -> "/enterEmailUrl",
            "deskproServiceName"        -> "serviceName",
            "backUrl"                   -> "/back",
            "serviceTitle"              -> "title",
            "emailAddress"              -> "aa@bb.cc"
          )
        )
        val secondResponse: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
            .withHttpHeaders("Authorization" -> "Bearer123")
            .post(Json.obj("passcode" -> "passcode"))
        )
        secondResponse.status shouldBe OK
        secondResponse.json   shouldBe Json.obj("status" -> "complete", "continueUrl" -> "/continueUrl")

        val verified: WSResponse = await(resourceRequest(s"/email-verification/verification-status/$credId").get())
        verified.status shouldBe OK
        verified.json shouldBe Json.obj(
          "emails" -> Json.arr(
            Json.obj(
              "emailAddress" -> "aa@bb.cc",
              "verified"     -> true,
              "locked"       -> false
            )
          )
        )
      }
      "verify a second email address if there is already exists a verified email via the passcode journey" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa2@bb.cc"),
          Some("/enterEmailUrl"),
          None,
          None,
          "passcode",
          0,
          0,
          0
        )

        expectJourneyToExist(journey)
        expectEmailsToBeStored(List(VerificationStatus("aa@bb.cc", verified = true, locked = false), VerificationStatus("aa2@bb.cc", verified = false, locked = false)))
        expectUserToBeAuthorisedWithGG(credId)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
            .withHttpHeaders("Authorization" -> "Bearer123")
            .post(Json.obj("passcode" -> "passcode"))
        )
        result.status shouldBe OK
        result.json   shouldBe Json.obj("status" -> "complete", "continueUrl" -> "/continueUrl")

        val verified: WSResponse = await(resourceRequest(s"/email-verification/verification-status/$credId").get())
        verified.status shouldBe OK

        (verified.json \ "emails")
          .as[List[JsObject]]
          .contains(
            Json.obj(
              "emailAddress" -> "aa@bb.cc",
              "verified"     -> true,
              "locked"       -> false
            )
          ) shouldBe true

        (verified.json \ "emails")
          .as[List[JsObject]]
          .contains(
            Json.obj(
              "emailAddress" -> "aa2@bb.cc",
              "verified"     -> true,
              "locked"       -> false
            )
          ) shouldBe true
      }
    }

    "the journey ID is valid but the passcode is incorrect" should {
      "return 400 Bad Request and the enter email URL" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa@bb.cc"),
          Some("/enterEmailUrl"),
          Some("/back"),
          Some("title"),
          "passcode",
          0,
          0,
          0
        )

        expectJourneyToExist(journey)
        expectUserToBeAuthorisedWithGG(credId)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
            .withHttpHeaders("Authorization" -> "Bearer123")
            .post(Json.obj("passcode" -> "not the right passcode"))
        )
        result.status shouldBe BAD_REQUEST
        result.json shouldBe Json.obj(
          "status" -> "incorrectPasscode",
          "journey" -> Json.obj(
            "accessibilityStatementUrl" -> "/accessibility",
            "enterEmailUrl"             -> "/enterEmailUrl",
            "deskproServiceName"        -> "serviceName",
            "backUrl"                   -> "/back",
            "serviceTitle"              -> "title",
            "emailAddress"              -> "aa@bb.cc"
          )
        )
      }
    }

    "the journey ID is valid but the email is not provided" should {
      "return 403 FORBIDDEN" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          None,
          Some("/enterEmailUrl"),
          None,
          None,
          "passcode",
          0,
          4,
          0
        )

        expectJourneyToExist(journey)
        expectPasscodeEmailToBeSent(journey.passcode)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/resend-passcode")
            .post(Json.obj())
        )

        result.status shouldBe FORBIDDEN
        result.json shouldBe Json.obj("status" -> "noEmailProvided")
      }
    }

    "the journey ID is invalid" should {
      "return 404 Not Found" in new Setup {
        expectUserToBeAuthorisedWithGG(credId)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/nope/passcode")
            .withHttpHeaders("Authorization" -> "Bearer123")
            .post(Json.obj("passcode" -> "passcode"))
        )

        result.status shouldBe NOT_FOUND
        result.json   shouldBe Json.obj("status" -> "journeyNotFound")
      }
    }

    "the journey ID is valid but too many attempts have been made" should {
      "return 403 Forbidden and the continue URL" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          credId,
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa@bb.cc"),
          Some("/enterEmailUrl"),
          None,
          None,
          "passcode",
          0,
          0,
          100
        )

        expectJourneyToExist(journey)
        expectUserToBeAuthorisedWithGG(credId)
        val emailsToBeStored: List[VerificationStatus] = List(
          VerificationStatus("aa@bb.cc", verified = false, locked = false)
        )
        expectEmailsToBeStored(emailsToBeStored)

        val result: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
            .withHttpHeaders(AUTHORIZATION -> "Bearer some_auth_token_probably")
            .post(Json.obj("passcode" -> "passcode"))
        )
        result.status shouldBe FORBIDDEN
        result.json   shouldBe Json.obj("status" -> "tooManyAttempts", "continueUrl" -> "/continueUrl")

        val response: WSResponse = await(resourceRequest(s"/email-verification/verification-status/$credId").get())
        response.status shouldBe 200
        response.json shouldBe Json.obj(
          "emails" -> Json.arr(
            Json.obj(
              "emailAddress" -> "aa@bb.cc",
              "verified"     -> false,
              "locked"       -> true
            )
          )
        )

      }

      "given 4 passcode entry failures, with a following passcode entry failure, the next passcode entry will fail" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          "credId",
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa@bb.cc"),
          Some("/enterEmailUrl"),
          None,
          None,
          "passcode",
          0,
          0,
          4
        )

        expectJourneyToExist(journey)
        expectEmailsToBeStored(List(VerificationStatus("aa@bb.cc", verified = false, locked = false)))
        expectUserToBeAuthorisedWithGG(credId)

        val firstResponse: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
            .withHttpHeaders("Authorization" -> "Bearer123")
            .post(Json.obj("passcode" -> "not the right passcode"))
        )
        firstResponse.status shouldBe BAD_REQUEST
        firstResponse.json shouldBe Json.obj(
          "status" -> "incorrectPasscode",
          "journey" -> Json.obj(
            "accessibilityStatementUrl" -> "/accessibility",
            "enterEmailUrl"             -> "/enterEmailUrl",
            "deskproServiceName"        -> "serviceName",
            "emailAddress"              -> "aa@bb.cc"
          )
        )

        val secondResponse: WSResponse = await(
          resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
            .withHttpHeaders(AUTHORIZATION -> "Bearer some_auth_token_probably")
            .post(Json.obj("passcode" -> "passcode"))
        )
        secondResponse.status shouldBe FORBIDDEN
        secondResponse.json   shouldBe Json.obj("status" -> "tooManyAttempts", "continueUrl" -> "/continueUrl")

      }

      "maxPasscodeAttempts should match with config value" in new Setup {
        val journey: Journey = Journey(
          UUID.randomUUID().toString,
          credId,
          "/continueUrl",
          "origin",
          "/accessibility",
          "serviceName",
          English,
          Some("aa@bb.cc"),
          Some("/enterEmailUrl"),
          None,
          None,
          "passcode",
          0,
          0,
          0
        )

        expectJourneyToExist(journey)
        expectUserToBeAuthorisedWithGG(credId)
        val emailsToBeStored: List[VerificationStatus] = List(
          VerificationStatus("aa@bb.cc", verified = false, locked = false)
        )
        expectEmailsToBeStored(emailsToBeStored)

        (0 to maxPasscodeAttempts).map { _ =>
          val result = await(
            resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
              .withHttpHeaders(AUTHORIZATION -> "Bearer some_auth_token_probably")
              .post(Json.obj("passcode" -> "passcode"))
          )
          result.status
        }.toList shouldBe List.tabulate(maxPasscodeAttempts)(_ => 200) ++ List(403)

      }
    }
  }

  "GET /verification-status/:credId" when {
    "we have a variety of emails stored" should {
      "return 200 and a list of the locked or verified ones" in new Setup {
        val emailsToBeStored: List[VerificationStatus] = List(
          VerificationStatus("email1", verified = true, locked = false),
          VerificationStatus("email2", verified = false, locked = false),
          VerificationStatus("email3", verified = false, locked = true)
        )

        expectEmailsToBeStored(emailsToBeStored)

        val response: WSResponse = await(resourceRequest(s"/email-verification/verification-status/$credId").get())

        response.status shouldBe 200

        (response.json \ "emails").as[JsArray].value should contain(
          Json.obj(
            "emailAddress" -> "email1",
            "verified"     -> true,
            "locked"       -> false
          )
        )
        (response.json \ "emails").as[JsArray].value should contain(
          Json.obj(
            "emailAddress" -> "email3",
            "verified"     -> false,
            "locked"       -> true
          )
        )

        verifyEmailVerificationOutcomeEventFired(1, """{"emailAddress":"email1","verified":true,"locked":false}""", "Email address is verified or locked", 200)
        verifyEmailVerificationOutcomeEventFired(1, """{"emailAddress":"email3","verified":false,"locked":true}""", "Email address is verified or locked", 200)

      }
    }

    "we have no records stored" should {
      "return 404 with an error message" in new Setup {

        val response: WSResponse = await(resourceRequest(s"/email-verification/verification-status/$credId").get())

        response.status shouldBe 404

        response.json shouldBe Json.obj("error" -> s"no verified or locked emails found for cred ID: $credId")

        verifyEmailVerificationOutcomeEventFired(1, "[]", "Email address not found or expired", 404)
      }
    }
  }

  trait Setup extends TestData with Eventually {

    def verifyEmailRequestEventFired(times: Int = 1, emailAddress: String, expectedStatus: Int): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"VerifyEmailRequest""""))
            .withRequestBody(containing(s""""credId":"$credId""""))
            .withRequestBody(containing(s""""bearerToken":"-"""))
            .withRequestBody(containing(s""""origin":"$origin""""))
            .withRequestBody(containing(s""""continueUrl":"$continueUrl""""))
            .withRequestBody(containing(s""""deskproServiceName":"$deskproServiceName""""))
            .withRequestBody(containing(s""""accessibilityStatementUrl":"$accessibilityStatementUrl""""))
            .withRequestBody(containing(s""""pageTitle":"-""""))
            .withRequestBody(containing(s""""backUrl":"-""""))
            .withRequestBody(containing(s""""emailAddress":"$emailAddress""""))
            .withRequestBody(containing(s""""emailEntryUrl":"$emailEntryUrl""""))
            .withRequestBody(containing(s""""lang":"$lang""""))
            .withRequestBody(containing(s""""statusCode":"${expectedStatus.toString}""""))
        )
      }
    }

    def verifyEmailVerificationOutcomeEventFired(times: Int = 1, emailJson: String, expectedTransactionName: String, expectedStatus: Int): Unit = {
      eventually {
        verify(
          times,
          postRequestedFor(urlEqualTo("/write/audit"))
            .withRequestBody(containing(""""auditType":"EmailVerificationOutcomeRequest""""))
            .withRequestBody(containing(s""""transactionName":"HMRC Gateway - Email Verification - $expectedTransactionName""""))
            .withRequestBody(containing(s""""credId":"$credId""""))
            .withRequestBody(containing(s""""bearerToken""""))
            .withRequestBody(containing(s""""userAgentString":"AHC/2.1""""))
            .withRequestBody(containing(emailJson))
            .withRequestBody(containing(s""""statusCode":"${expectedStatus.toString}""""))
        )
      }
    }

    def expectEmailToSendSuccessfully(): StubMapping = {
      stubFor(post(urlEqualTo("/hmrc/email")).willReturn(ok()))
    }

    def expectPasscodeEmailToBeSent(passcode: String): Unit = {
      stubFor(
        post("/hmrc/email")
          .withRequestBody(matchingJsonPath("parameters.passcode", equalTo(passcode)))
          .willReturn(ok)
      )
    }

    def expectEmailToFailToSend(): StubMapping = {
      stubFor(post(urlEqualTo("/hmrc/email")).willReturn(serverError()))
    }

    def expectEmailsToBeStored(emails: List[VerificationStatus]): Unit = {

      val inserts: Seq[Future[InsertOneResult]] = emails.map(email => {
        verificationStatusRepo.collection
          .insertOne(
            VerificationStatusMongoRepository.Entity(
              credId = credId,
              emailAddress = email.emailAddress,
              verified = email.verified,
              locked = email.locked,
              createdAt = Instant.now
            )
          )
          .toFuture()
      })

      await(Future.sequence(inserts))
    }
    def expectJourneyToExist(journey: Journey): Unit =
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
              passcodeAttempts = journey.passcodeAttempts
            )
          )
          .toFuture()
      )

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
  }

  trait TestData {

    val credId: String = UUID.randomUUID().toString
    val continueUrl = "/plastic-packaging-tax/start"
    val origin = "ppt"
    val deskproServiceName = "plastic-packaging-tax"
    val emailAddress = "barrywood@hotmail.com"
    val emailEntryUrl = "/start"
    val accessibilityStatementUrl = "/accessibility"
    val lang = "en"

    def verifyEmailPayload(emailAddress: String = emailAddress): JsObject = Json.obj(
      "credId"                    -> credId,
      "continueUrl"               -> continueUrl,
      "origin"                    -> origin,
      "deskproServiceName"        -> deskproServiceName,
      "accessibilityStatementUrl" -> accessibilityStatementUrl,
      "email" -> Json.obj(
        "address"  -> emailAddress,
        "enterUrl" -> emailEntryUrl
      ),
      "lang" -> lang
    )

    def verifyEmailWithLabelsPayload(emailAddress: String = emailAddress, labels: JsObject): JsObject = Json.obj(
      "credId"                    -> credId,
      "continueUrl"               -> continueUrl,
      "origin"                    -> origin,
      "deskproServiceName"        -> deskproServiceName,
      "accessibilityStatementUrl" -> accessibilityStatementUrl,
      "email" -> Json.obj(
        "address"  -> emailAddress,
        "enterUrl" -> emailEntryUrl
      ),
      "lang"   -> lang,
      "labels" -> labels
    )
  }
}
