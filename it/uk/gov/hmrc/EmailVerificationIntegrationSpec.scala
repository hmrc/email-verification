package uk.gov.hmrc

import _root_.play.api.libs.json.Json
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.GivenWhenThen
import support.{IntegrationBaseSpec, WireMockConfig, WireMockHelper}
import uk.gov.hmrc.crypto.Crypted.fromBase64
import uk.gov.hmrc.crypto.CryptoWithKeysFromConfig

import scala.collection.JavaConverters._

class EmailVerificationIntegrationSpec extends IntegrationBaseSpec(testName = "EmailVerificationIntegrationSpec".takeRight(30),
  extraConfig = Map("microservice.services.email.port" -> WireMockConfig.stubPort.toString))
  with GivenWhenThen with WireMockHelper {

  val emailToVerify = "example@domain.com"
  val templateId = "my-lovely-template"
  val templateParams = Map("name" -> "Mr Joe Bloggs")
  val continueUrl = "http://some/url"

  lazy val crypto = CryptoWithKeysFromConfig("application.secret")

  "email verification" should {

    "send the verification email to the specified address successfully" in new Setup {
      Given("The email service is running")
      stubSendEmailRequest(202)

      When("a client submits a verification request")

      val response = await(appClient("/request-verification").post(Json.parse(request)))
      response.status shouldBe 204

      Then("an email is sent")
      verifyEmailSent(emailToVerify, templateId, paramsWithVerificationLink)

    }

    "return 502 error if email sending fails" in new Setup {
      val body = "some-5xx-message"
      stubSendEmailRequest(500, body)
      val response = await(appClient("/request-verification").post(Json.parse(request)))
      response.status shouldBe 502
      response.body should include (body)
    }

    "return 400 error if email sending fails with 400" in new Setup {
      val body = "some-4xx-message"
      stubSendEmailRequest(400, body)
      val response = await(appClient("/request-verification").post(Json.parse(request)))
      response.status shouldBe 400
      response.body should include (body)
    }
  }

  def verifyEmailSent(to: String, templateId: String, params: Map[String, String]): Unit = {
    val emailSendRequest = WireMock.findAll(emailEventStub).asScala.head.getBodyAsString
    val emailSendRequestJson = Json.parse(emailSendRequest)

    (emailSendRequestJson \ "to").as[Seq[String]] shouldBe Seq(to)
    (emailSendRequestJson \ "templateId").as[String] shouldBe templateId

    val verificationLink = (emailSendRequestJson \ "parameters" \ "verificationLink").as[String]

    val decryptedTokenJson = decryptToJson(verificationLink.split("token=")(1))

    (decryptedTokenJson \ "email").as[String] shouldBe emailToVerify
    (decryptedTokenJson \ "continueUrl").as[String] shouldBe continueUrl
  }

  def decryptToJson(encrypted: String) = {
    val base64DecodedEncrypted = fromBase64(encrypted)
    val decrypted = crypto.decrypt(base64DecodedEncrypted).value
    Json.parse(decrypted)
  }

  private val emailMatchingStrategy = urlEqualTo("/send-templated-email")
  private val emailEventStub = postRequestedFor(emailMatchingStrategy)

  def stubSendEmailRequest(status: Int, body: String) =
    stubFor(post(emailMatchingStrategy).willReturn(aResponse()
      .withStatus(status)
      .withBody(body)))

  def stubSendEmailRequest(status: Int) =
    stubFor(post(emailMatchingStrategy).willReturn(aResponse()
      .withStatus(status)))

  trait Setup {
    val paramsJsonStr = Json.toJson(templateParams).toString()
    val expectedVerificationLink = "http://localhost:9890/verification?token=UG85NW1OcWdjR29xS29EM1pIQ1NqMlpzOEduemZCeUhvZVlLNUVtU2c3emp2TXZzRmFRSzlIdjJBTkFWVVFRUkg1M21MRUY4VE1TWDhOZ0hMNmQ0WHRQQy95NDZCditzNHd6ZUhpcEoyblNsT3F0bGJmNEw5RnhjOU0xNlQ3Y2o1dFdYVUE0NGFSUElURFRrSS9HRHhoTFZxdU9YRkw4OTZ4Z0tOTWMvQTJJd1ZqR3NJZ0pTNjRJNVRUc2RpcFZ1MjdOV1dhNUQ3OG9ITkVlSGJnaUJyUT09"
    val paramsWithVerificationLink = templateParams + ("verificationLink" -> expectedVerificationLink)

    val request =
      s"""{
          |  "email": "$emailToVerify",
          |  "templateId": "$templateId",
          |  "templateParameters": $paramsJsonStr,
          |  "linkExpiryDuration" : "P2D",
          |  "continueUrl" : "$continueUrl"
          |}""".stripMargin
  }

}
