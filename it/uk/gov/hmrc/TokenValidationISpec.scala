package uk.gov.hmrc

import _root_.play.api.libs.json.Json
import org.joda.time.Period
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen}
import support.IntegrationBaseSpec
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import concurrent.ExecutionContext.Implicits.global

class TokenValidationISpec extends IntegrationBaseSpec("TokenValidationISpec", extraConfig = Map("mongodb.uri" -> "mongodb://localhost:27017/Test-email-verification")) with GivenWhenThen with BeforeAndAfterEach with BeforeAndAfterAll {

  lazy val tokenRepo = VerificationTokenMongoRepository()
  lazy val verifiedRepo = VerifiedEmailMongoRepository()
  val token = "token"
  implicit val hc = HeaderCarrier()

  "the service" should {

    "return 204 if the token is valid" in {
      Given("a verification request exists")
      tokenRepo.insert(token, "user@email.com", Period.minutes(5))

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
    }
  }

  override def beforeEach() {
    super.beforeEach()
    await(tokenRepo.drop)
    await(verifiedRepo.drop)
    await(verifiedRepo.ensureIndexes)
  }

  override def afterAll() {
    await(tokenRepo.drop)
    await(verifiedRepo.drop)
    super.afterAll()
  }
}
