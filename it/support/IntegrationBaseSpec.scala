package support

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Play.current
import play.api.libs.ws.WS
import support.EmailStub._
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.play.it.ServiceSpec

import scala.concurrent.ExecutionContext.Implicits.global

class IntegrationBaseSpec(additionalConfig: Map[String, String] = Map.empty) extends ServiceSpec
    with ScalaFutures
    with IntegrationPatience
    with WireMockHelper
    with BeforeAndAfterEach
{

  private val baseConfig: Map[String, String] = Map(
    "microservice.services.email.port" -> WireMockConfig.stubPort.toString,
    "microservice.services.platform-analytics.port" -> WireMockConfig.stubPort.toString,
    "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
    "mongodb.uri" -> "mongodb://localhost:27017/Test-email-verification"
  )

  override val server = new IntegrationServer(getClass.getSimpleName, baseConfig ++ additionalConfig)

  def appClient(path: String) = WS.url(resource(s"/email-verification$path"))

  private lazy val tokenRepo = VerificationTokenMongoRepository()
  private lazy val verifiedRepo = VerifiedEmailMongoRepository()

  override def beforeEach() {
    super.beforeEach()
    await(tokenRepo.drop)
    await(verifiedRepo.drop)
    await(verifiedRepo.ensureIndexes)
    AnalyticsStub.stubAnalyticsEvent()
  }

  override def afterAll() {
    await(tokenRepo.drop)
    await(verifiedRepo.drop)
    super.afterAll()
  }

  def tokenFor(email: String) = {
    stubSendEmailRequest(202)
    appClient("/verification-requests").post(verificationRequest(emailToVerify = email)).futureValue.status shouldBe 201
    decryptedToken(lastVerificationEMail)._1.get
  }
}
