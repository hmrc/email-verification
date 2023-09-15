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

import play.api.libs.json.{JsObject, JsString, Json}
import support.BaseISpec
import support.EmailStub._

class PayloadHandlingISpec extends BaseISpec {

  "a POST request for email verification" should {

    "return CREATED when given a valid payload" in new Setup {
      expectEmailToBeSent()

      val response = await(wsClient.url(appClient("/verification-requests")).post(validPayload))

      response.status shouldBe CREATED
    }

    "return CREATED when template parameters are not provided" in new Setup {
      expectEmailToBeSent()
      val validPayloadWithMissingTemplateParameter: JsObject = validPayload - "templateParameters"

      val response = await(wsClient.url(appClient("/verification-requests")).post(validPayloadWithMissingTemplateParameter))

      response.status shouldBe CREATED
    }

    "return CREATED when continueUrl is relative" in new Setup {
      expectEmailToBeSent()
      val validPayloadWithRelativeContinueUrl: JsObject = validPayload ++ Json.obj("continueUrl" -> "/continue")

      val response = await(wsClient.url(appClient("/verification-requests")).post(validPayloadWithRelativeContinueUrl))

      response.status shouldBe CREATED
    }

    "return BAD_REQUEST with structured error when continueUrl is not a valid URL" in new Setup {
      val validPayloadWithInvalidContinueUrl: JsObject = validPayload ++ Json.obj("continueUrl" -> "not-a-valid-url-$#$#$")

      val response = await(wsClient.url(appClient("/verification-requests")).post(validPayloadWithInvalidContinueUrl))

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
      val invalidPayloadWithMissingEmailField: JsObject = validPayload - "email"

      val response = await(wsClient.url(appClient("/verification-requests")).post(invalidPayloadWithMissingEmailField))

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
      val invalidPayloadWithMissingEmailAndTemplateIdField: JsObject = (validPayload - "email") - "templateId"

      val response = await(wsClient.url(appClient("/verification-requests")).post(invalidPayloadWithMissingEmailAndTemplateIdField))

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
      val invalidPayloadWithMissingEmailField: JsObject = validPayload ++ Json.obj("linkExpiryDuration" -> "XXX")

      val response = await(wsClient.url(appClient("/verification-requests")).post(invalidPayloadWithMissingEmailField))

      response.status shouldBe BAD_REQUEST
      (response.json \\ "code").head shouldBe JsString("VALIDATION_ERROR")
    }

    "return BAD_GATEWAY if upstream service fails with INTERNAL_SERVER_ERROR" in {
      val body = "some-5xx-message"
      expectEmailServiceToRespond(INTERNAL_SERVER_ERROR, body)

      val response = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest()))

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
      expectEmailServiceToRespond(NOT_FOUND, body)

      val response = await(wsClient.url(appClient("/verification-requests")).post(verificationRequest()))

      response.status shouldBe BAD_GATEWAY
      response.json shouldBe Json.parse(
        """{
          |  "code": "UPSTREAM_ERROR",
          |  "message": "POST of 'http://localhost:11111/hmrc/email' returned 404. Response body: 'some-4xx-message'"
          |}""".stripMargin
      )
    }

    "return NOT_FOUND error if URL doesn't exist" in {
      val response = await(wsClient.url(appClient("/non-existent-url")).post(verificationRequest()))

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

  trait Setup {
    val validPayload: JsObject = Json.obj(
      "templateId" -> "some-template-id",
      "email" -> "abc@def.com",
      "templateParameters" -> Json.obj(),
      "linkExpiryDuration" -> "P2D",
      "continueUrl" -> "http://example.com/continue"
    )
  }

}
