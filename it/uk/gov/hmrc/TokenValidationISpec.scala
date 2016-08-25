package uk.gov.hmrc

import _root_.play.api.libs.json.Json
import org.scalatest.GivenWhenThen
import support.EmailStub._
import support.IntegrationBaseSpec
import uk.gov.hmrc.play.http.HeaderCarrier

class TokenValidationISpec extends IntegrationBaseSpec with GivenWhenThen {
  implicit val hc = HeaderCarrier()

  "the service" should {
    "return 201 if the token is valid and 204 if email is already verified" in {
      Given("a verification request exists")
      val token = tokenFor("user@email.com").get

      When("a token verification request is submitted")
      val response = appClient("/verified-email-addresses").post(Json.obj("token" -> token)).futureValue

      Then("Service responds with Created")
      response.status shouldBe 201

      When("the same token is submitted again")
      val response2 = appClient("/verified-email-addresses").post(Json.obj("token" -> token)).futureValue

      Then("Service responds with NoContent")
      response2.status shouldBe 204
    }

    "return 400 if the token is invalid or expired" in  {
      When("an invalid token verification request is submitted")
      val response = appClient("/verified-email-addresses").post(Json.obj("token" -> "invalid")).futureValue
      response.status shouldBe 400
      response.body shouldBe "Token not found or expired"
    }
  }

  def tokenFor(email: String) = {
    stubSendEmailRequest(202)
    appClient("/verification-requests").post(verificationRequest(emailToVerify = email)).futureValue.status shouldBe 204
    decryptedToken(lastVerificationEMail)._1
  }
}
