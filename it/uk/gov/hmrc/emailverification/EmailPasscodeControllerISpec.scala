package uk.gov.hmrc.emailverification

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import org.joda.time.DateTime
import org.scalatest.Assertion
import play.api.libs.json.{JsDefined, JsString, Json}
import support.BaseISpec
import support.EmailStub._
import uk.gov.hmrc.emailverification.models.PasscodeDoc
import uk.gov.hmrc.emailverification.repositories.PasscodeMongoRepository
import uk.gov.hmrc.http.HeaderNames

import scala.concurrent.ExecutionContext.Implicits.global

class EmailPasscodeControllerISpec extends BaseISpec {

  override def extraConfig = super.extraConfig ++ Map("auditing.enabled" -> true)

  "testOnlyGetPasscode" should {
    "return a 200 with passcode if passcode exists in repository against sessionId" in new Setup {
      val passcode = generateUUID
      expectPasscodeToBePopulated(passcode)

      val response = await(resourceRequest("/test-only/passcode").withHttpHeaders(HeaderNames.xSessionId -> sessionId).get())
      response.status shouldBe 200
      Json.parse(response.body) \ "passcode" shouldBe JsDefined(JsString(passcode))
    }

    "return a 400 if a sessionId wasnt provided with the request" in new Setup {
      val response = await(resourceRequest("/test-only/passcode").get())
      response.status shouldBe 400
      Json.parse(response.body) \ "code" shouldBe JsDefined(JsString("BAD_PASSCODE_REQUEST"))
      Json.parse(response.body) \ "message" shouldBe JsDefined(JsString("No session id provided"))
    }

    "return a 404 if the session id is in the request but not in mongo" in new Setup {
      val response = await(resourceRequest("/test-only/passcode").withHttpHeaders(HeaderNames.xSessionId -> sessionId).get())
      response.status shouldBe 404
      Json.parse(response.body) \ "code" shouldBe JsDefined(JsString("PASSCODE_NOT_FOUND_OR_EXPIRED"))
      Json.parse(response.body) \ "message" shouldBe JsDefined(JsString("No passcode found for sessionId"))
    }
  }

  "request-passcode" should {
    "send the a passcode email to the specified address successfully" in new Setup {
      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("a client submits a passcode email request")

      val response = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response.status shouldBe 201

      Then("a passcode email is sent")

      Thread.sleep(200)

      verifyEmailSentWithPasscode(emailToVerify)
      verifySendEmailWithPinFired(202)
    }

    "fail 6th email passcode request with Forbidden as limit of 5 is breached" in new Setup {
      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("a client submits a passcode email request 5 times")
      await(wsClient.url(appClient("/request-passcode")).withHttpHeaders(HeaderNames.xSessionId -> sessionId).post(passcodeRequest(emailToVerify))).status shouldBe 201

      Thread.sleep(200)
      WireMock.reset()

      When("a client submits a passcode email request the 6th time")
      val response = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response.status shouldBe 403

      Then("a passcode email is NOT sent")

      Thread.sleep(200)

      verifyNoEmailSent
    }

    "only latest passcode sent to a given email should be valid" in new Setup {
      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("client submits a passcode request")
      val response1 = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response1.status shouldBe 201
      val passcode1 = lastPasscodeEmailed

      When("client submits a second passcode request for same email")
      val response2 = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response2.status shouldBe 201
      val passcode2 = lastPasscodeEmailed

      Then("only the last passcode sent should be valid")
      val validationResponse1 = await(wsClient.url(appClient("/verify-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeVerificationRequest(passcode1)))

      val validationResponse2 = await(wsClient.url(appClient("/verify-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeVerificationRequest(passcode2)))

      validationResponse1.status shouldBe 400
      validationResponse2.status shouldBe 201

      verifySendEmailWithPinFired(202)
    }

    "verifying an unknown passcode should return a 400 error" in new Setup {
      When("client submits an unknown passcode to verification request")
      val response = await(wsClient.url(appClient("/verify-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeVerificationRequest("NDJRMS")))
      response.status shouldBe 400
      (Json.parse(response.body) \ "code").as[String] shouldBe "PASSCODE_NOT_FOUND_OR_EXPIRED"

      verifyCheckEmailVerifiedFired(emailVerified = false)
    }

    "uppercase passcode verification is valid" in new Setup {
      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("client requests a passcode")
      val response1 = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response1.status shouldBe 201
      val uppercasePasscode = lastPasscodeEmailed.toUpperCase

      Then("submitting verification request with passcode in lowercase should be successful")
      await(wsClient.url(appClient("/verify-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeVerificationRequest(uppercasePasscode))).status shouldBe 201

      verifyCheckEmailVerifiedFired(emailVerified = true)
    }

    "lowercase passcode verification is valid" in new Setup {
      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("client requests a passcode")
      val response1 = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response1.status shouldBe 201
      val lowercasePasscode = lastPasscodeEmailed.toLowerCase

      Then("submitting verification request with passcode in lowercase should be successful")
      await(wsClient.url(appClient("/verify-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeVerificationRequest(lowercasePasscode))).status shouldBe 201

      verifyCheckEmailVerifiedFired(emailVerified = true)
    }

    "second passcode verification request with same session and passcode should return 204 instead of 201 response" in new Setup {
      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("client submits a passcode verification request")
      val response1 = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response1.status shouldBe 201
      val passcode = lastPasscodeEmailed

      Then("the request to verify with passcode should be successful")
      await(wsClient.url(appClient("/verify-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeVerificationRequest(passcode))).status shouldBe 201
      Then("an additional request to verify the same passcode should be successful, but return with a 204 response")
      await(wsClient.url(appClient("/verify-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeVerificationRequest(passcode))).status shouldBe 204

      verifyCheckEmailVerifiedFired(emailVerified = true, 2)
    }

    "passcode verification for two different emails should be successful" in new Setup {
      def submitVerificationRequest(emailToVerify: String, templateId: String): Assertion = {
        val response = await(wsClient.url(appClient("/request-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeRequest(emailToVerify)))
        response.status shouldBe 201
        val passcode = lastPasscodeEmailed
        And("the client verifies the token")
        await(wsClient.url(appClient("/verify-passcode"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(passcodeVerificationRequest(passcode))).status shouldBe 201
        Then("the verified email should also have been stored")
        await(wsClient.url(appClient("/verified-email-check"))
          .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
          .post(Json.obj("email" -> emailToVerify))).status shouldBe 200
      }

      Given("The email service is running")
      expectEmailServiceToRespond(202)

      When("client submits first verification request ")
      submitVerificationRequest("example1@domain.com", templateId)

      When("client submits second verification request ")
      submitVerificationRequest("example2@domain.com", templateId)

      verifyCheckEmailVerifiedFired(emailVerified = true, 2)
    }


    "return 502 error if email sending fails" in new Setup {
      val body = "some-5xx-message"
      expectEmailServiceToRespond(500, body)
      val response = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response.status shouldBe 502
      response.body should include(body)

      verifySendEmailWithPinFired(500)
    }

    "return BAD_EMAIL_REQUEST error if email sending fails with 400" in new Setup {
      val body = "some-400-message"
      expectEmailServiceToRespond(400, body)
      val response = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response.status shouldBe 400
      response.body should include(body)

      (Json.parse(response.body) \ "code").as[String] shouldBe "BAD_EMAIL_REQUEST"

      verifySendEmailWithPinFired(400)
    }

    "return 500 error if email sending fails with 4xx" in new Setup {
      val body = "some-4xx-message"
      expectEmailServiceToRespond(404, body)
      val response = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response.status shouldBe 502
      response.body should include(body)

      verifySendEmailWithPinFired(404)
    }

    "return 400 error if no sessionID is provided" in new Setup {
      val body = "some-4xx-message"
      expectEmailServiceToRespond(404, body)
      val response = await(wsClient.url(appClient("/request-passcode"))
        .post(passcodeRequest(emailToVerify)))
      response.status shouldBe 400
      Json.parse(response.body) \ "code" shouldBe JsDefined(JsString("BAD_REQUEST"))
      Json.parse(response.body) \ "message" shouldBe JsDefined(JsString("No session id provided"))
    }

    "return 409 if email is already verified" in new Setup {
      assumeEmailAlreadyVerified(emailToVerify, templateId)

      val response = await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify)))
      response.status shouldBe 409

      Json.parse(
        """{
          |"code":"EMAIL_VERIFIED_ALREADY",
          |"message":"Email has already been verified"
          |}""".stripMargin).toString()
    }
  }

  trait Setup {
    val templateId = "my-lovely-template"
    val emailToVerify = "example@domain.com"
    val sessionId = UUID.randomUUID.toString
    val passcodeMongoRepository = app.injector.instanceOf(classOf[PasscodeMongoRepository])


    def assumeEmailAlreadyVerified(email: String, templateId: String): Assertion = {
      expectEmailServiceToRespond(202)
      await(wsClient.url(appClient("/request-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeRequest(emailToVerify))).status shouldBe 201
      val passcode = lastPasscodeEmailed
      await(wsClient.url(appClient("/verify-passcode"))
        .withHttpHeaders(HeaderNames.xSessionId -> sessionId)
        .post(passcodeVerificationRequest(passcode))).status shouldBe 201
    }

    def expectPasscodeToBePopulated(passcode: String) = {
      val expiresAt = DateTime.now().plusHours(2)
      val email = generateUUID
      passcodeMongoRepository.insert(PasscodeDoc(sessionId, email, passcode, expiresAt))
    }

    def generateUUID = UUID.randomUUID().toString

    def verifySendEmailWithPinFired(responseCode: Int) = {
      Thread.sleep(100)
      verify(postRequestedFor(urlEqualTo("/write/audit"))
        .withRequestBody(containing(""""auditType":"SendEmailWithPin""""))
        .withRequestBody(containing(s""""responseCode":"$responseCode""""))
      )
    }

    def verifyCheckEmailVerifiedFired(emailVerified: Boolean, times: Int = 1) = {
      Thread.sleep(100)
      verify(times, postRequestedFor(urlEqualTo("/write/audit"))
        .withRequestBody(containing(""""auditType":"CheckEmailVerified""""))
        .withRequestBody(containing(s""""emailVerified":"$emailVerified""""))
      )
    }

  }

}
