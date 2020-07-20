package uk.gov.hmrc.emailverification

import java.util.UUID

import com.fasterxml.jackson.annotation.ObjectIdGenerators.UUIDGenerator
import org.scalatest.Assertion
import play.api.libs.json.Json
import support.BaseISpec
import support.EmailStub._

class EmailPasscodeControllerISpec extends BaseISpec {
  val emailToVerify = "example@domain.com"
  val sessionId = UUID.randomUUID.toString

  "request-passcode" should {
    "send the a passcode email to the specified address successfully" in new Setup {
      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("a client submits a passcode email request")

      withClient { ws =>
        val response = await(ws.url(appClient("/request-passcode")).post(passcodeRequest(sessionId, emailToVerify, templateId)))
        response.status shouldBe 201

        Then("a passcode email is sent")
        verifyEmailSentWithPasscode(emailToVerify, templateId, paramsWithVerificationLink)
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
        await(ws.url(appClient("/verify-passcode")).post(Json.obj("sessiondId"->sessionId, "passcode" -> passcode1))).status shouldBe 400
        await(ws.url(appClient("/verify-passcode")).post(Json.obj("sessiondId"->sessionId, "passcode" -> passcode2))).status shouldBe 201
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

        //START HERE

        Then("the verification request with the token should be successful")
        await(ws.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> passcode))).status shouldBe 201
        Then("an additional verification requests with the token should be successful, but return with a 204 response")
          await(ws.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status shouldBe 204
      }
    }

    "email verification for two different emails should be successful" in new Setup {
      def submitVerificationRequest(emailToVerify: String, templateId: String, continueUrl: String): Assertion = {
        withClient { ws =>
          val response = await(ws.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify, templateId, continueUrl)))
          response.status shouldBe 201
          val token = decryptedToken(lastVerificationEMail)._1.get
          And("the client verifies the token")
          await(ws.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status shouldBe 201
          Then("the email should be verified")
            await(ws.url(appClient("/verified-email-check")).post(Json.obj("email" -> emailToVerify))).status shouldBe 200
        }
      }

      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("client submits first verification request ")
      submitVerificationRequest("example1@domain.com", templateId, continueUrl)

      When("client submits second verification request ")
      submitVerificationRequest("example2@domain.com", templateId, continueUrl)
    }

    "return 502 error if email sending fails" in new Setup {
      val body = "some-5xx-message"
      expectEmailServiceToRespond(500, body)
      withClient { ws =>
        val response = await(ws.url(appClient("/verification-requests")).post(verificationRequest()))
        response.status shouldBe 502
        response.body should include(body)
      }
    }

    "return BAD_EMAIL_REQUEST error if email sending fails with 400" in new Setup {
      val body = "some-400-message"
      expectEmailServiceToRespond(400, body)
      withClient { ws =>
        val response = await(ws.url(appClient("/verification-requests")).post(verificationRequest()))
        response.status shouldBe 400
        response.body should include(body)

        (Json.parse(response.body) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"
      }
    }

    "return 500 error if email sending fails with 4xx" in new Setup {
      val body = "some-4xx-message"
      expectEmailServiceToRespond(404, body)
      withClient { ws =>
        val response = await(ws.url(appClient("/verification-requests")).post(verificationRequest()))
        response.status shouldBe 502
        response.body should include(body)
      }
    }

    "return 409 if email is already verified" in new Setup {
      assumeEmailAlreadyVerified(emailToVerify)

      withClient { ws =>
        val response = await(ws.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify)))
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

  def assumeEmailAlreadyVerified(email: String): Assertion = {
    expectEmailServiceToRespond(202)
    withClient { ws =>
      await(ws.url(appClient("/verification-requests")).post(verificationRequest(email))).status shouldBe 201
      val token = tokenFor(email)
      await(ws.url(appClient("/verified-email-addresses")).post(Json.obj("token" -> token))).status shouldBe 201
    }
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
