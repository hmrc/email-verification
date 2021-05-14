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
import play.api.libs.json.Json
import play.api.test.Injecting

class JourneyWireMockSpec extends BaseISpec with Injecting {


  "POST /verify-email" when {
    "given a valid payload and the email was successfully sent out" should {
      "return a redirect url to the journey endpoint on the frontend" in new Setup {
        expectEmailToSendSuccessfully()
        val response = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload))

        val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

        response.status shouldBe CREATED
        (response.json \ "redirectUri").as[String] should fullyMatch regex s"/email-verification-frontend/journey/$uuidRegex\\?continueUrl=$continueUrl&origin=$origin"
println(verifyEmailPayload.toString())

      }
    }

    "given a valid payload and the email failed to send" should {
      "return a redirect url to the journey endpoint on the frontend" in new Setup {
        expectEmailToSendUnSuccessfully()

        val response = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload))

        response.status shouldBe BAD_GATEWAY
      }
    }
  }


  trait Setup extends TestData {

    def expectEmailToSendSuccessfully() = {
      stubFor(post(urlEqualTo("/hmrc/email")).willReturn(ok()))
    }

    def expectEmailToSendUnSuccessfully() = {
      stubFor(post(urlEqualTo("/hmrc/email")).willReturn(serverError()))
    }

  }

  trait TestData {

    val credId = UUID.randomUUID().toString
    val continueUrl = "/plastic-packaging-tax/start"
    val origin = "ppt"
    val deskproServiceName = "plastic-packaging-tax"
    val emailAddress = "barrywood@hotmail.com"

    val verifyEmailPayload = Json.obj(
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

