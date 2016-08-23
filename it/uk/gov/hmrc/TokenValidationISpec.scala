package uk.gov.hmrc

import _root_.play.api.libs.json.Json
import org.scalatest.GivenWhenThen
import support.IntegrationBaseSpec

class TokenValidationISpec extends IntegrationBaseSpec("TokenValidationISpec") with GivenWhenThen {

  "a token" should {

    "be validated" in {

      When("a token verification request is submitted")
      val response = appClient("/email-verification/verified-email-addresses").post(Json.obj("token" -> someToken)).futureValue


      response.status shouldBe 204
    }
  }
}
