package uk.gov.hmrc

import _root_.play.api.libs.json.Json
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.GivenWhenThen
import support.{IntegrationBaseSpec, WireMockConfig, WireMockHelper}

class EmailVerificationIntegrationSpec extends IntegrationBaseSpec(testName = "EmailVerificationIntegrationSpec",
  extraConfig = Map("microservice.services.email.port" -> WireMockConfig.stubPort.toString))
  with GivenWhenThen with WireMockHelper {


  "email verification" should {

    "send the verification email to the specified address successfully" in new Setup{
      Given("The email service is running")
      stubSendEmailRequest(202)

      When("a client submits a verification request")


      val response = await(appClient("/email-verifications").post(Json.parse(request)))
      response.status shouldBe 204

      Then("an email is sent")
      verifyEmailSent(emailToVerify, templateId, Map("name" -> "Mr Joe Bloggs"))
    }

    "return 500 if email sending fails" in new Setup {
      stubSendEmailRequest(500)
      val response = await(appClient("/email-verifications").post(Json.parse(request)))
      response.status shouldBe 502
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

  def stubSendEmailRequest(status: Int) = stubFor(post(urlEqualTo("/send-templated-email"))
    .willReturn(aResponse()
      .withStatus(status)))

  trait Setup {
    val emailToVerify = "example@domain.com"
    val templateId = "my-lovely-template"
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
  }

}
