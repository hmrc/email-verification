package uk.gov.hmrc

import _root_.play.api.libs.json.Json
import org.scalatest.GivenWhenThen
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
                     |  "errors":[
                     |    {"fieldName":"obj.email","message":"error.path.missing"}
                     |  ]
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
          |  "errors":[
          |    {"fieldName":"obj.templateId","message":"error.path.missing"},
          |    {"fieldName":"obj.email","message":"error.path.missing"}
          |  ]
          |}""".stripMargin).toString()
    }
  }


}
