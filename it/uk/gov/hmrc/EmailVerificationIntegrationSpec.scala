package uk.gov.hmrc

import _root_.play.api.libs.json.Json
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.GivenWhenThen
import support.{IntegrationBaseSpec, WireMockConfig, WireMockHelper}

class EmailVerificationIntegrationSpec extends IntegrationBaseSpec(testName = "EmailVerificationIntegrationSpec",
  extraConfig = Map("microservice.services.email.port" -> WireMockConfig.stubPort.toString))
  with GivenWhenThen with WireMockHelper {


  "email verification" should {

    "send the verification email to the specified address" in {

      val emailToVerify = "example@domain.com"
      val templateId = "my-lovely-template"
      Given("The email service is running")
      expectSendEmailRequest()

      When("a client submits a verification request")
      val request =
        s"""{
            |  "email": "$emailToVerify",
            |  "templateId": "$templateId",
            |  "templateParameters": {
            |    "name": "Mr Joe Bloggs"
            |  },
            |  "linkExpiryDuration" : "P2D",
            |  "continueUrl" : "http://some/url"
            |}""".stripMargin

      val response = await(appClient("/email-verifications").post(Json.parse(request)))
      response.status shouldBe 204

      Then("an email is sent")
      verifyEmailSent(emailToVerify, templateId, Map("name" -> "Mr Joe Bloggs"))
    }
  }

  def verifyEmailSent(to: String, templateId: String, params: Map[String, String]): Unit = {
    val json = Json.obj(
      "to" -> Seq(to),
      "templateId" -> templateId,
      "parameters" -> Json.toJson(params)
    )

    verify(1, postRequestedFor(urlEqualTo("/send-templated-email"))
      .withRequestBody(equalTo(json.toString())))

  }

  def expectSendEmailRequest() = stubFor(post(urlEqualTo("/send-templated-email"))
    .willReturn(aResponse()
      .withStatus(202)))

}
