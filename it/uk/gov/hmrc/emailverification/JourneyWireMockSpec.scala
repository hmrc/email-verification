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
import uk.gov.hmrc.emailverification.models.VerificationStatus
import uk.gov.hmrc.emailverification.repositories.VerificationStatusMongoRepository
import uk.gov.hmrc.emailverification.repositories.VerificationStatusMongoRepository.Entity

class JourneyWireMockSpec extends BaseISpec with Injecting {

  val verificationStatusMongoRepository: VerificationStatusMongoRepository = inject[VerificationStatusMongoRepository]

  "POST /verify-email" when {
    "given a valid payload and the email was successfully sent out" should {
      "return a redirect url to the journey endpoint on the frontend" in new Setup {
        expectEmailToSendSuccessfully()
        val response = await(resourceRequest("/email-verification/verify-email").post(verifyEmailPayload))

        val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

        response.status shouldBe CREATED
        (response.json \ "redirectUri").as[String] should fullyMatch regex s"/email-verification/journey/$uuidRegex\\?continueUrl=$continueUrl&origin=$origin"
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

  "GET /verification-status/:credId" when {
    "we have a variety of emails stored" should {
      "return 200 and a list of the locked or verified ones" in new Setup {
        val emailsToBeStored = List(
          VerificationStatus(Some("email1"), verified = true, locked = false),
          VerificationStatus(Some("email2"), verified = false, locked = false),
          VerificationStatus(Some("email3"), verified = false, locked = true),
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

    def expectEmailToSendSuccessfully() = {
      stubFor(post(urlEqualTo("/hmrc/email")).willReturn(ok()))
    }

    def expectEmailToSendUnSuccessfully() = {
      stubFor(post(urlEqualTo("/hmrc/email")).willReturn(serverError()))
    }

    def expectEmailsToBeStored(emails: List[VerificationStatus]): Unit = {
      await(
        verificationStatusMongoRepository.bulkInsert(
          emails.map(email =>
            Entity(
              credId = credId,
              emailAddress = email.emailAddress,
              verified = email.verified,
              locked = email.locked,
              createdAt = DateTime.now()
            )
          )
        )(scala.concurrent.ExecutionContext.global)
      )
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

