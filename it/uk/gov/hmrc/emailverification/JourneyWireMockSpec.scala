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

import java.util.UUID
import support.BaseISpec
import com.github.tomakehurst.wiremock.client.WireMock._
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.test.Injecting
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.emailverification.models.{English, Journey, VerificationStatus}
import reactivemongo.play.json.ImplicitBSONHandlers._

import scala.concurrent.ExecutionContext

class JourneyWireMockSpec extends BaseISpec with Injecting {

  "POST /verify-email" when {
    "given a valid payload and the email was successfully sent out" should {
      "return a redirect url to the journey endpoint on the frontend" in new Setup {
        expectEmailToSendSuccessfully()
        val response = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload()))

        val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

        response.status shouldBe CREATED
        (response.json \ "redirectUri").as[String] should fullyMatch regex s"/email-verification/journey/$uuidRegex/passcode\\?continueUrl=$continueUrl&origin=$origin"
      }
    }

    "given a valid payload but the email was previosuly locked out" should {
      "return unauthorised response" in new Setup {
        val emailsToBeStored = List(
          VerificationStatus("email1", verified = true, locked = false),
          VerificationStatus("email2", verified = false, locked = true),
        )

        expectEmailsToBeStored(emailsToBeStored)
        val response = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload("email2")))
        response.status shouldBe 401
      }
    }

    "given a valid payload and the email failed to send" should {
      "return a redirect url to the journey endpoint on the frontend" in new Setup {
        expectEmailToFailToSend()

        val response = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload()))

        response.status shouldBe BAD_GATEWAY
      }
    }
  }

  "GET /journey/:journeyId" should {
    "return 200 OK and the frontend journey data when the journey ID is valid" in new Setup {
      val journey = Journey(
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

      val result = await(resourceRequest(s"/email-verification/journey/${journey.journeyId}").get())
      result.status shouldBe OK
      result.json shouldBe Json.obj(
        "accessibilityStatementUrl" -> "/accessibility",
        "enterEmailUrl" -> "/enterEmail",
        "deskproServiceName" -> "serviceName",
        "backUrl" -> "/back",
        "serviceTitle" -> "title"
      )
    }

    "return 404 Not Found when the journey ID is invalid" in new Setup {
      val result = await(resourceRequest("/email-verification/journey/nope").get())
      result.status shouldBe NOT_FOUND
    }
  }

  "POST /journey/:journeyId/email" when {
    "the journey ID is valid and more attempts are allowed" should {
      "send the passcode email and return 200 OK" in new Setup {
        val journey = Journey(
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

        val result = await(resourceRequest(s"/email-verification/journey/${journey.journeyId}/email")
          .post(Json.obj("email" -> "aaa@bbb.ccc")))
        result.status shouldBe OK
        result.json shouldBe Json.obj("status" -> "accepted")
      }
    }

    "the journey ID is invalid" should {
      "return 404 Not Found" in new Setup {
        val result = await(resourceRequest("/email-verification/journey/nope/email")
          .post(Json.obj("email" -> "aaa@bbb.ccc")))

        result.status shouldBe NOT_FOUND
        result.json shouldBe Json.obj("status" -> "journeyNotFound")
      }
    }

    "the journey ID is valid but too many attempts have been made" should {
      "return 403 Forbidden and the continue URL" in new Setup {
        val journey = Journey(
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
          emailAddressAttempts = Int.MaxValue,
          0,
          0
        )

        expectJourneyToExist(journey)
        expectPasscodeEmailToBeSent(journey.passcode)

        val result = await(resourceRequest(s"/email-verification/journey/${journey.journeyId}/email")
          .post(Json.obj("email" -> "aaa@bbb.ccc")))
        result.status shouldBe FORBIDDEN
        result.json shouldBe Json.obj(
          "status" -> "tooManyAttempts",
          "continueUrl" -> "/continueUrl"
        )
      }
    }
  }

  "POST /journey/:journeyId/resend-passcode" when {
    "the journey ID is valid and more attempts are allowed in the session" should {
      "resend the passcode email and return 200 OK" in new Setup {
        val journey = Journey(
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

        val result = await(resourceRequest(s"/email-verification/journey/${journey.journeyId}/resend-passcode")
          .post(Json.obj()))
        result.status shouldBe OK
        result.json shouldBe Json.obj("status" -> "passcodeResent")
      }
    }

    "the journey ID is invalid" should {
      "return 404 Not Found" in new Setup {
        val result = await(resourceRequest(s"/email-verification/journey/nope/resend-passcode")
          .post(Json.obj()))
        result.status shouldBe NOT_FOUND
      }
    }

    "the journey ID is valid but too many attempts have been made for the current email address" should {
      "return 403 Forbidden and the frontend journey data" in new Setup {
        val journey = Journey(
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
          passcodesSentToEmail = Int.MaxValue,
          passcodeAttempts = 0
        )

        expectJourneyToExist(journey)

        val result = await(resourceRequest(s"/email-verification/journey/${journey.journeyId}/resend-passcode")
          .post(Json.obj()))
        result.status shouldBe FORBIDDEN
        result.json shouldBe Json.obj(
          "status" -> "tooManyAttemptsForEmail",
          "journey" -> Json.obj(
            "accessibilityStatementUrl" -> "/accessibility",
            "enterEmailUrl" -> "/enterEmailUrl",
            "deskproServiceName" -> "serviceName",
            "backUrl" -> "/back",
            "serviceTitle" -> "title",
            "emailAddress"->"aa@bb.cc"
          )
        )
      }
    }

    "the journey ID is valid but too many attempts have been made in the session" should {
      "return 403 Forbidden and the continue URL" in new Setup {
        val journey = Journey(
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
          passcodeAttempts = Int.MaxValue
        )

        expectJourneyToExist(journey)

        val result = await(resourceRequest(s"/email-verification/journey/${journey.journeyId}/resend-passcode")
          .post(Json.obj()))
        result.status shouldBe FORBIDDEN
        result.json shouldBe Json.obj("status" -> "tooManyAttemptsInSession", "continueUrl" -> "/continueUrl")
      }
    }
  }

  "POST /journey/:journeyId/passcode" when {
    "the journey ID is valid and the passcode is correct" should {
      "verify the email address and return 200 OK and the continue URL" in new Setup {
        val journey = Journey(
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

        val result = await(resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
          .post(Json.obj("passcode" -> "passcode")))
        result.status shouldBe OK
        result.json shouldBe Json.obj("status" -> "complete", "continueUrl" -> "/continueUrl")

        val verified = await(resourceRequest(s"/email-verification/verification-status/$credId").get())
        verified.status shouldBe OK
        verified.json shouldBe Json.obj(
          "emails" -> Json.arr(
            Json.obj(
              "emailAddress" -> "aa@bb.cc",
              "verified" -> true,
              "locked" -> false
            )
          )
        )
      }
    }

    "the journey ID is valid but the passcode is incorrect" should {
      "return 400 Bad Request and the enter email URL" in new Setup {
        val journey = Journey(
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

        val result = await(resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
          .post(Json.obj("passcode" -> "not the right passcode")))
        result.status shouldBe BAD_REQUEST
        result.json shouldBe Json.obj(
          "status" -> "incorrectPasscode",
          "journey" -> Json.obj(
            "accessibilityStatementUrl" -> "/accessibility",
            "enterEmailUrl" -> "/enterEmailUrl",
            "deskproServiceName" -> "serviceName",
            "backUrl" -> "/back",
            "serviceTitle" -> "title",
            "emailAddress" -> "aa@bb.cc"
          )
        )
      }
    }

    "the journey ID is invalid" should {
      "return 404 Not Found" in new Setup {
        expectUserToBeAuthorisedWithGG(credId)

        val result = await(resourceRequest(s"/email-verification/journey/nope/passcode")
          .post(Json.obj("passcode" -> "passcode")))

        result.status shouldBe NOT_FOUND
        result.json shouldBe Json.obj("status" -> "journeyNotFound")
      }
    }

    "the journey ID is valid but too many attempts have been made" should {
      "return 403 Forbidden and the continue URL" in new Setup {
        val journey = Journey(
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
          Int.MaxValue
        )

        expectJourneyToExist(journey)
        expectUserToBeAuthorisedWithGG(credId)

        val result = await(resourceRequest(s"/email-verification/journey/${journey.journeyId}/passcode")
          .withHttpHeaders(AUTHORIZATION -> "Bearer some_auth_token_probably")
          .post(Json.obj("passcode" -> "passcode")))
        result.status shouldBe FORBIDDEN
        result.json shouldBe Json.obj("status" -> "tooManyAttempts", "continueUrl" -> "/continueUrl")
      }
    }
  }

  "GET /verification-status/:credId" when {
    "we have a variety of emails stored" should {
      "return 200 and a list of the locked or verified ones" in new Setup {
        val emailsToBeStored = List(
          VerificationStatus("email1", verified = true, locked = false),
          VerificationStatus("email2", verified = false, locked = false),
          VerificationStatus("email3", verified = false, locked = true),
        )

        expectEmailsToBeStored(emailsToBeStored)

        val response = await(resourceRequest(s"/email-verification/verification-status/$credId").get())

        response.status shouldBe 200

        response.json shouldBe Json.obj(
          "emails" -> Json.arr(
            Json.obj(
              "emailAddress" -> "email1",
              "verified" -> true,
              "locked" -> false
            ),
            Json.obj(
              "emailAddress" -> "email3",
              "verified" -> false,
              "locked" -> true
            ),
          )
        )
      }
    }

    "we have no records stored" should {
      "return 404 with an error message" in new Setup {

        val response = await(resourceRequest(s"/email-verification/verification-status/$credId").get())

        response.status shouldBe 404

        response.json shouldBe Json.obj("error" -> s"no verified or locked emails found for cred ID: $credId")

      }
    }
  }


  trait Setup extends TestData {
    val verificationStatusRepo = mongoConnectorForTest.db().collection[JSONCollection]("verificationStatus")
    val journeyRepo = mongoConnectorForTest.db().collection[JSONCollection]("journey")

    def expectEmailToSendSuccessfully() = {
      stubFor(post(urlEqualTo("/hmrc/email")).willReturn(ok()))
    }

    def expectPasscodeEmailToBeSent(passcode: String): Unit = {
      stubFor(
        post("/hmrc/email")
          .withRequestBody(matchingJsonPath("parameters.passcode", equalTo(passcode)))
          .willReturn(ok)
      )
    }

    def expectEmailToFailToSend() = {
      stubFor(post(urlEqualTo("/hmrc/email")).willReturn(serverError()))
    }

    def expectEmailsToBeStored(emails: List[VerificationStatus]): Unit = {
      await(
        verificationStatusRepo.insert(false).many(emails.map { email =>
          Json.obj(
            "credId" -> credId,
            "emailAddress" -> email.emailAddress,
            "verified" -> email.verified,
            "locked" -> email.locked,
            "createdAt" -> Json.obj("$date" -> DateTime.now.getMillis)
          )
        })(ExecutionContext.global, JsObjectDocumentWriter)
      )
    }

    def expectJourneyToExist(journey: Journey): Unit = {
      await(
        journeyRepo.insert(false).one(Json.obj(
          "_id" -> journey.journeyId,
          "credId" -> journey.credId,
          "continueUrl" -> journey.continueUrl,
          "origin" -> journey.origin,
          "accessibilityStatementUrl" -> journey.accessibilityStatementUrl,
          "serviceName" -> journey.serviceName,
          "language" -> journey.language,
          "emailAddress" -> journey.emailAddress,
          "enterEmailUrl" -> journey.enterEmailUrl,
          "backUrl" -> journey.backUrl,
          "pageTitle" -> journey.pageTitle,
          "passcode" -> journey.passcode,
          "createdAt" -> Json.obj("$date" -> DateTime.now.getMillis),
          "emailAddressAttempts" -> journey.emailAddressAttempts,
          "passcodesSentToEmail" -> journey.passcodesSentToEmail,
          "passcodeAttempts" -> journey.passcodeAttempts,
        ))(ExecutionContext.global, JsObjectDocumentWriter)
      )
    }

    def expectUserToBeAuthorisedWithGG(credId: String): Unit = {
      stubFor(post("/auth/authorise")
        .willReturn(okJson(Json.obj(
          "optionalCredentials" -> Json.obj(
            "providerId" -> credId,
            "providerType" -> "GG"
          )
        ).toString())))
    }
  }

  trait TestData {

    val credId = UUID.randomUUID().toString
    val continueUrl = "/plastic-packaging-tax/start"
    val origin = "ppt"
    val deskproServiceName = "plastic-packaging-tax"
    val emailAddress = "barrywood@hotmail.com"

    def verifyEmailPayload(emailAddress:String=emailAddress) = Json.obj(
      "credId" -> credId,
      "continueUrl" -> continueUrl,
      "origin" -> origin,
      "deskproServiceName" -> deskproServiceName,
      "accessibilityStatementUrl" -> "/accessibility",
      "email" -> Json.obj(
        "address" -> emailAddress,
        "enterUrl" -> "/start",
      ),
      "lang" -> "en"
    )


  }

}

