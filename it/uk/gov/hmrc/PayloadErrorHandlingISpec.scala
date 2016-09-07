package uk.gov.hmrc

import _root_.play.api.libs.json.Json
import org.scalatest.GivenWhenThen
import support.EmailStub._
import support.IntegrationBaseSpec

class PayloadErrorHandlingISpec extends IntegrationBaseSpec with GivenWhenThen {
  "request POST on an endpoint" should {
    "return BAD REQUEST with structured error when invalid payload is sent with a required field missing" in {
      When("a client submits an invalid verification request")

      val invalidPayloadWithMissingEmailField = Json.parse(
        """{
            |  "templateId":  "my-lovely-template",
            |  "templateParameters": {},
            |  "linkExpiryDuration" : "P2D",
            |  "continueUrl" : "http://some/url"
            |}""".stripMargin)

      val response = appClient("/verification-requests").post(invalidPayloadWithMissingEmailField).futureValue
      response.status shouldBe 400
      response.body shouldBe
        Json.parse("""{
                     |  "code":"VALIDATION_ERROR",
                     |  "message":"Payload validation failed",
                     |  "details":{
                     |    "obj.email": "error.path.missing"
                     |  }
                     |}""".stripMargin).toString()
    }

    "return BAD REQUEST with structured error containint all fields when invalid payload is sent with mutiple fields missing" in {
      When("a client submits an invalid verification request")

      val invalidPayloadWithMissingEmailAndTemplateIdField = Json.parse(
        """{
            |  "templateParameters": {},
            |  "linkExpiryDuration" : "P2D",
            |  "continueUrl" : "http://some/url"
            |}""".stripMargin)

      val response = appClient("/verification-requests").post(invalidPayloadWithMissingEmailAndTemplateIdField).futureValue
      response.status shouldBe 400
      response.body shouldBe
        Json.parse("""{
          |  "code":"VALIDATION_ERROR",
          |  "message":"Payload validation failed",
          |  "details":{
          |    "obj.templateId": "error.path.missing",
          |    "obj.email":"error.path.missing"
          |  }
          |}""".stripMargin).toString()
    }

    "return BAD REQUEST with structured error when invalid payload is sent with invalid linkExpiryDuration" in {
      When("a client submits an invalid verification request")

      val invalidPayloadWithMissingEmailField = Json.parse(
        """{
          |  "email": "email@email.com",
          |  "templateId":  "my-lovely-template",
          |  "templateParameters": {},
          |  "linkExpiryDuration" : "XXX",
          |  "continueUrl" : "http://some/url"
          |}""".stripMargin)

      val response = appClient("/verification-requests").post(invalidPayloadWithMissingEmailField).futureValue
      response.status shouldBe 400
      response.body shouldBe
        Json.parse("""{
                     |  "code":"VALIDATION_ERROR",
                     |  "message":"Invalid format: \"XXX\""
                     |}""".stripMargin).toString()
    }

    "return 502 error if upstream service fails with 500" in {
      val body = "some-5xx-message"
      stubSendEmailRequest(500, body)
      val response = appClient("/verification-requests").post(verificationRequest()).futureValue
      response.status shouldBe 502
      response.body shouldBe
        Json.parse("""{
                     |  "code":"UPSTREAM_ERROR",
                     |  "message":"POST of 'http://localhost:11111/send-templated-email' returned 500. Response body: 'some-5xx-message'"
                     |}""".stripMargin).toString()
    }


    "return 502 error if upstream service fails with 4xx" in {
      val body = "some-4xx-message"
      stubSendEmailRequest(404, body)
      val response = appClient("/verification-requests").post(verificationRequest()).futureValue
      response.status shouldBe 502
      response.body shouldBe
        Json.parse("""{
                     |  "code":"UPSTREAM_ERROR",
                     |  "message":"POST of 'http://localhost:11111/send-templated-email' returned 404 (Not Found). Response body: 'some-4xx-message'"
                     |}""".stripMargin).toString()
    }

    "return 404 error if url not found" in  {
      val response = appClient("/non-existent-url").post(verificationRequest()).futureValue
      response.status shouldBe 404
      response.body shouldBe
        Json.parse("""{
                     |  "code":"NOT_FOUND",
                     |  "message":"URI not found",
                     |  "details": {
                     |    "requestedUrl":"/email-verification/non-existent-url"
                     |  }
                     |}""".stripMargin).toString()
    }
  }

}
