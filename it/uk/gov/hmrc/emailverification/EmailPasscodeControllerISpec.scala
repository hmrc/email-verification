package uk.gov.hmrc.emailverification

import java.util.UUID

import org.scalatest.Assertion
import play.api.libs.json.Json
import support.BaseISpec
import support.EmailStub._

class EmailPasscodeControllerISpec extends BaseISpec {

  "request-passcode" should {
    "send the a passcode email to the specified address successfully" in new Setup {
      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("a client submits a passcode email request")

      withClient { ws =>
        val response = await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId)))
        response.status shouldBe 201

        Then("a passcode email is sent")
        verifyEmailSentWithPasscode(emailToVerify, templateId)
      }
    }

    "only latest passcode sent to a given email should be valid" in new Setup {
      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("client submits a passcode request")
      withClient { ws =>
        val response1 = await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId)))
        response1.status shouldBe 201
        val passcode1 = lastPasscodeEmailed

        When("client submits a second passcode request for same email")
        val response2 = await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId)))
        response2.status shouldBe 201
        val passcode2 = lastPasscodeEmailed

        Then("only the last passcode sent should be valid")
        val validationResponse1 = await(ws.url(appClient("/verify-passcode")).post(passcodeVerificationRequest(sessionId, passcode1)))
        val validationResponse2 = await(ws.url(appClient("/verify-passcode")).post(passcodeVerificationRequest(sessionId, passcode2)))

        validationResponse1.status shouldBe 400
        validationResponse2.status shouldBe 201
      }
    }

    "verifying an unknown passcode should return a 400 error" in new Setup {
      When("client submits a passcode verification request")
      withClient { ws =>
        Then("only the last passcode sent should be valid")
        val response = await(ws.url(appClient("/verify-passcode")).post(passcodeVerificationRequest(sessionId, "NDJRMS")))
        response.status shouldBe 400
        (Json.parse(response.body) \ "code").as[String] shouldBe "PASSCODE_NOT_FOUND_OR_EXPIRED"
      }
    }

    "second passcode verification request with same passcode should return 204 instead of 201 response" in new Setup {
      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("client submits a passcode verification request")
      withClient { ws =>
        val response1 = await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId)))
        response1.status shouldBe 201
        val passcode = lastPasscodeEmailed

        Then("the request to verify with passcode should be successful")
        await(ws.url(appClient("/verify-passcode")).post(passcodeVerificationRequest(sessionId, passcode))).status shouldBe 201
        Then("an additional request to verify the same passcode should be successful, but return with a 204 response")
        await(ws.url(appClient("/verify-passcode")).post(passcodeVerificationRequest(sessionId, passcode))).status shouldBe 204
      }
    }

    "passcode verification for two different emails should be successful" in new Setup {
      def submitVerificationRequest(emailToVerify: String, templateId: String): Assertion = {
        withClient { ws =>
          val response = await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId)))
          response.status shouldBe 201
          val passcode = lastPasscodeEmailed
          And("the client verifies the token")
          await(ws.url(appClient("/verify-passcode")).post(passcodeVerificationRequest(sessionId, passcode))).status shouldBe 201
          Then("the verified email should also have been stored")
          await(ws.url(appClient("/verified-email-check")).post(Json.obj("email" -> emailToVerify))).status shouldBe 200
        }
      }

      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("client submits first verification request ")
      submitVerificationRequest("example1@domain.com", templateId)

      When("client submits second verification request ")
      submitVerificationRequest("example2@domain.com", templateId)
    }

    "return 502 error if email sending fails" in new Setup {
      val body = "some-5xx-message"
      expectEmailServiceToRespond(500, body)
      withClient { ws =>
        val response = await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId)))
        response.status shouldBe 502
        response.body should include(body)
      }
    }

    "return BAD_EMAIL_REQUEST error if email sending fails with 400" in new Setup {
      val body = "some-400-message"
      expectEmailServiceToRespond(400, body)
      withClient { ws =>
        val response = await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId)))
        response.status shouldBe 400
        response.body should include(body)

        (Json.parse(response.body) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"
      }
    }

    "return 500 error if email sending fails with 4xx" in new Setup {
      val body = "some-4xx-message"
      expectEmailServiceToRespond(404, body)
      withClient { ws =>
        val response = await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId)))
        response.status shouldBe 502
        response.body should include(body)
      }
    }

    "return 409 if email is already verified" in new Setup {
      assumeEmailAlreadyVerified(emailToVerify, templateId)

      withClient { ws =>
        val response = await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId)))
        response.status shouldBe 409
        response.body shouldBe
          Json.parse(
            """{
              |"code":"EMAIL_VERIFIED_ALREADY",
              |"message":"Email has already been verified"
              |}""".stripMargin).toString()
      }
    }
  }


  trait Setup {
    val templateId = "my-lovely-template"
    val emailToVerify = "example@domain.com"
    val sessionId = UUID.randomUUID.toString

    def assumeEmailAlreadyVerified(email: String, templateId:String): Assertion = {
      expectEmailServiceToRespond(202)
      withClient { ws =>
        await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId))).status shouldBe 201
        val passcode = lastPasscodeEmailed
        await(ws.url(appClient("/verify-passcode")).post(passcodeVerificationRequest(sessionId, passcode))).status shouldBe 201
      }
    }

  }

}
