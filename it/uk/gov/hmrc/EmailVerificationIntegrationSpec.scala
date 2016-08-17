package uk.gov.hmrc

import _root_.play.api.Play.current
import _root_.play.api.libs.ws.WS
import _root_.play.api.libs.json.Json
import org.scalatest.GivenWhenThen
import uk.gov.hmrc.play.it._

class EmailVerificationIntegrationSpec extends ServiceSpec with GivenWhenThen {
  "email verification" should {

    "send the verification email to the specified address" in {
      Given("")

      When("a client submits a verification request")
      val request = """{
                      |  "email": "example@domain.com",
                      |  "templateId": "my-lovely-template",
                      |  "templateParameters": {
                      |    "name": "Mr Joe Bloggs"
                      |  },
                      |  "linkExpiryDuration" : "P2D",
                      |  "continueUrl" : "http://some/url"
                      |}""".stripMargin

      val response = await(appClient("/email-verifications").post(Json.parse(request)))
      println(response.body)
      response.status shouldBe 204

      Then("an email is sent")
    }
  }

  def appClient(path: String) = WS.url(resource(s"/email-verification$path"))
  override val server = new IntegrationServer(getClass.getSimpleName.takeRight(30))

}
