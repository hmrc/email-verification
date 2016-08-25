package uk.gov.hmrc

import _root_.play.api.libs.json.Json
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}
import support.EmailStub._
import support.{IntegrationBaseSpec, WireMockConfig, WireMockHelper}
import uk.gov.hmrc.emailverification.repositories.{VerifiedEmail, VerifiedEmailMongoRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class EmailVerificationISpec extends IntegrationBaseSpec(testName = "EmailVerificationISpec",
  extraConfig = Map(
    "microservice.services.email.port" -> WireMockConfig.stubPort.toString,
    "queryParameter.encryption.key" -> "mRX1FSPQ9qCzZ61V9PBh3XuU24l6xhI4VenkXhN0uDs")
  ) with GivenWhenThen with WireMockHelper with BeforeAndAfterEach {

  val emailToVerify = "example@domain.com"
  val templateId = "my-lovely-template"
  val templateParams = Map("name" -> "Mr Joe Bloggs")
  val continueUrl = "http://some/url"
  lazy val verifiedRepo = VerifiedEmailMongoRepository()

  "email verification" should {
    "send the verification email to the specified address successfully" in new Setup {
      Given("The email service is running")
      stubSendEmailRequest(202)

      When("a client submits a verification request")

      val response = appClient("/verification-requests").post(Json.parse(request)).futureValue
      response.status shouldBe 204

      Then("an email is sent")
      verifyEmailSent(emailToVerify, continueUrl, templateId, paramsWithVerificationLink)
    }

    "return 502 error if email sending fails" in new Setup {
      val body = "some-5xx-message"
      stubSendEmailRequest(500, body)
      val response = appClient("/verification-requests").post(Json.parse(request)).futureValue
      response.status shouldBe 502
      response.body should include (body)
    }

    "return 400 error if email sending fails with 400" in new Setup {
      val body = "some-400-message"
      stubSendEmailRequest(400, body)
      val response = appClient("/verification-requests").post(Json.parse(request)).futureValue
      response.status shouldBe 400
      response.body should include (body)
    }

    "return 500 error if email sending fails with 4xx" in new Setup {
      val body = "some-4xx-message"
      stubSendEmailRequest(404, body)
      val response = appClient("/verification-requests").post(Json.parse(request)).futureValue
      response.status shouldBe 500
      response.body should include (body)
    }

    "return 409 if email is already verified" in new Setup {
      verifiedRepo.insert(VerifiedEmail(emailToVerify)).futureValue
      val response = appClient("/verification-requests").post(Json.parse(request)).futureValue
      response.status shouldBe 409
    }
  }

  override def beforeEach() = {
    super.beforeEach()
    verifiedRepo.drop.futureValue
  }

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
