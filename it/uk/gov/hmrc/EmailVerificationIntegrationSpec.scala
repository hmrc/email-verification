package uk.gov.hmrc

import _root_.play.api.libs.json.Json
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.GivenWhenThen
import support.{IntegrationBaseSpec, WireMockConfig, WireMockHelper}

class EmailVerificationIntegrationSpec extends IntegrationBaseSpec(testName = "EmailVerificationIntegrationSpec",
  extraConfig = Map("microservice.services.email.port" -> WireMockConfig.stubPort.toString))
  with GivenWhenThen with WireMockHelper {

  "email verification" should {

    "send the verification email to the specified address successfully" in new Setup {
      Given("The email service is running")
      stubSendEmailRequest(202)

      When("a client submits a verification request")

      val response = await(appClient("/email-verifications").post(Json.parse(request)))
      response.status shouldBe 204

      Then("an email is sent")
      verifyEmailSent(emailToVerify, templateId, paramsWithVerificationLink)
    }

    "send the verification email 2 times if verification was called 2 times for the same recipient email" in new Setup {
      Given("The email service is running")
      stubSendEmailRequest(202)

      When("a client submits a verification request")

      val firstResponse = await(appClient("/email-verifications").post(Json.parse(request)))
      firstResponse.status shouldBe 204

      val secondResponse = await(appClient("/email-verifications").post(Json.parse(request)))
      secondResponse.status shouldBe 204

      Then("an email is sent 2 times")
      verifyEmailSent(emailToVerify, templateId, paramsWithVerificationLink, expectedTimes = 2)
    }

    "return 502 error if email sending fails" in new Setup {
      stubSendEmailRequest(500)
      val response = await(appClient("/email-verifications").post(Json.parse(request)))
      response.status shouldBe 502
    }
  }

  def verifyEmailSent(to: String, templateId: String, params: Map[String, String], expectedTimes: Int = 1): Unit = {
    val json = Json.obj(
      "to" -> Seq(to),
      "templateId" -> templateId,
      "parameters" -> Json.toJson(params)
    )

    verify(expectedTimes, postRequestedFor(urlEqualTo("/send-templated-email"))
      .withRequestBody(equalTo(json.toString())))

  }

  def stubSendEmailRequest(status: Int) = stubFor(post(urlEqualTo("/send-templated-email"))
    .willReturn(aResponse()
      .withStatus(status)))

  trait Setup {
    val emailToVerify = "example@domain.com"
    val templateId = "my-lovely-template"
    val templateParams = Map("name2" -> "Mr Joe Bloggs")
    val paramsJsonStr = Json.toJson(templateParams).toString()
    val paramsWithVerificationLink = templateParams + ("verificationLink" -> "")

    val request =
      s"""{
          |  "email": "$emailToVerify",
          |  "templateId": "$templateId",
          |  "templateParameters": $paramsJsonStr,
          |  "linkExpiryDuration" : "P2D",
          |  "continueUrl" : "http://some/url"
          |}""".stripMargin
  }

}
