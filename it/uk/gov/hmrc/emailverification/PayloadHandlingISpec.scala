package uk.gov.hmrc.emailverification

import play.api.http.Status._
import play.api.libs.json.Json
import support.EmailStub._
import support.IntegrationBaseSpec

class PayloadHandlingISpec extends IntegrationBaseSpec(
  additionalConfig = Map("whitelisted-domains" -> ",  test.example.com  ,,    , example.com ,example.com,example.com:1234")
) {

  "a POST request for email verification" should {

    "return CREATED when given a valid payload" in new Setup {
      stubSendEmailRequest(ACCEPTED)

      val response = appClient("/verification-requests").post(validPayload).futureValue

      response.status shouldBe CREATED
    }

    "return CREATED when template parameters are not provided" in new Setup {
      stubSendEmailRequest(ACCEPTED)
      val validPayloadWithMissingTemplateParameter = validPayload - "templateParameters"

      val response = appClient("/verification-requests").post(validPayloadWithMissingTemplateParameter).futureValue

      response.status shouldBe CREATED
    }

    "return CREATED when continueUrl is relative" in new Setup {
      stubSendEmailRequest(ACCEPTED)
      val validPayloadWithRelativeContinueUrl = validPayload ++ Json.obj("continueUrl" -> "/continue")

      val response = appClient("/verification-requests").post(validPayloadWithRelativeContinueUrl).futureValue

      response.status shouldBe CREATED
    }

    "return CREATED when continueUrl is protocol-relative and whitelisted" in new Setup {
      stubSendEmailRequest(ACCEPTED)
      val validPayloadWithRelativeContinueUrl = validPayload ++ Json.obj("continueUrl" -> "//example.com/continue")

      val response = appClient("/verification-requests").post(validPayloadWithRelativeContinueUrl).futureValue

      response.status shouldBe CREATED
    }

    "return BAD_REQUEST with structured error when continueUrl is absolute but not whitelisted" in new Setup {
      val validPayloadWithUnwhitelistedContinueUrl = validPayload ++ Json.obj("continueUrl" -> "http://hackers.ru/continue")

      val response = appClient("/verification-requests").post(validPayloadWithUnwhitelistedContinueUrl).futureValue

      response.status shouldBe BAD_REQUEST
      response.json shouldBe Json.parse(
        """{
          |  "code": "VALIDATION_ERROR",
          |  "message": "Payload validation failed",
          |  "details":{
          |    "obj.continueUrl": "URL is not whitelisted"
          |  }
          |}""".stripMargin
      )
    }

    "return BAD_REQUEST with structured error when continueUrl is protocol-relative but not whitelisted" in new Setup {
      val validPayloadWithUnwhitelistedContinueUrl = validPayload ++ Json.obj("continueUrl" -> "//hackers.ru/continue")

      val response = appClient("/verification-requests").post(validPayloadWithUnwhitelistedContinueUrl).futureValue

      response.status shouldBe BAD_REQUEST
      response.json shouldBe Json.parse(
        """{
          |  "code": "VALIDATION_ERROR",
          |  "message": "Payload validation failed",
          |  "details":{
          |    "obj.continueUrl": "URL is not whitelisted"
          |  }
          |}""".stripMargin
      )
    }

    "return BAD_REQUEST with structured error when continueUrl is not a valid URL" in new Setup {
      val validPayloadWithInvalidContinueUrl = validPayload ++ Json.obj("continueUrl" -> "not-a-valid-url-$#$#$")

      val response = appClient("/verification-requests").post(validPayloadWithInvalidContinueUrl).futureValue

      response.status shouldBe BAD_REQUEST
      response.json shouldBe Json.parse(
        """{
          |  "code": "VALIDATION_ERROR",
          |  "message": "Payload validation failed",
          |  "details":{
          |    "obj.continueUrl": "URL could not be parsed"
          |  }
          |}""".stripMargin
      )
    }

    "return BAD_REQUEST with structured error when invalid payload is sent with a required field missing" in new Setup {
      val invalidPayloadWithMissingEmailField = validPayload - "email"

      val response = appClient("/verification-requests").post(invalidPayloadWithMissingEmailField).futureValue

      response.status shouldBe BAD_REQUEST
      response.json shouldBe Json.parse(
        """{
          |  "code": "VALIDATION_ERROR",
          |  "message": "Payload validation failed",
          |  "details":{
          |    "obj.email": "error.path.missing"
          |  }
          |}""".stripMargin
      )
    }

    "return BAD_REQUEST with structured error containing all fields when invalid payload is sent with multiple fields missing" in new Setup {
      val invalidPayloadWithMissingEmailAndTemplateIdField = (validPayload - "email") - "templateId"

      val response = appClient("/verification-requests").post(invalidPayloadWithMissingEmailAndTemplateIdField).futureValue

      response.status shouldBe BAD_REQUEST
      response.json shouldBe Json.parse(
        """{
          |  "code": "VALIDATION_ERROR",
          |  "message": "Payload validation failed",
          |  "details": {
          |    "obj.templateId": "error.path.missing",
          |    "obj.email": "error.path.missing"
          |  }
          |}""".stripMargin
      )
    }

    "return BAD_REQUEST with structured error when invalid payload is sent with invalid linkExpiryDuration" in new Setup {
      val invalidPayloadWithMissingEmailField = validPayload ++ Json.obj("linkExpiryDuration" -> "XXX")

      val response = appClient("/verification-requests").post(invalidPayloadWithMissingEmailField).futureValue

      response.status shouldBe BAD_REQUEST
      response.json shouldBe Json.parse(
        """{
          |  "code": "VALIDATION_ERROR",
          |  "message": "Invalid format: \"XXX\""
          |}""".stripMargin
      )
    }

    "return BAD_GATEWAY if upstream service fails with INTERNAL_SERVER_ERROR" in {
      val body = "some-5xx-message"
      stubSendEmailRequest(INTERNAL_SERVER_ERROR, body)

      val response = appClient("/verification-requests").post(verificationRequest()).futureValue

      response.status shouldBe BAD_GATEWAY
      response.json shouldBe Json.parse(
        """{
          |  "code": "UPSTREAM_ERROR",
          |  "message": "POST of 'http://localhost:11111/hmrc/email' returned 500. Response body: 'some-5xx-message'"
          |}""".stripMargin
      )
    }

    "return BAD_GATEWAY if upstream service fails with 4xx" in {
      val body = "some-4xx-message"
      stubSendEmailRequest(NOT_FOUND, body)

      val response = appClient("/verification-requests").post(verificationRequest()).futureValue

      response.status shouldBe BAD_GATEWAY
      response.json shouldBe Json.parse(
        """{
          |  "code": "UPSTREAM_ERROR",
          |  "message": "POST of 'http://localhost:11111/hmrc/email' returned 404 (Not Found). Response body: 'some-4xx-message'"
          |}""".stripMargin
      )
    }

    "return NOT_FOUND error if URL doesn't exist" in  {
      val response = appClient("/non-existent-url").post(verificationRequest()).futureValue

      response.status shouldBe NOT_FOUND
      response.json shouldBe Json.parse(
        """{
          |  "code": "NOT_FOUND",
          |  "message": "URI not found",
          |  "details": {
          |    "requestedUrl": "/email-verification/non-existent-url"
          |  }
          |}""".stripMargin
      )
    }
  }

  sealed trait Setup {
    val validPayload = Json.obj(
      "templateId" -> "some-template-id",
      "email" -> "abc@def.com",
      "templateParameters" -> Json.obj(),
      "linkExpiryDuration" -> "P2D",
      "continueUrl" -> "http://example.com/continue"
    )
  }
}
