package support

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Play.current
import play.api.libs.ws.WS
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.play.it.ServiceSpec
import concurrent.ExecutionContext.Implicits.global

class IntegrationBaseSpec(extraConfig: Map[String, String] = Map(
  "microservice.services.email.port" -> WireMockConfig.stubPort.toString,
  "queryParameter.encryption.key" -> "mRX1FSPQ9qCzZ61V9PBh3XuU24l6xhI4VenkXhN0uDs",
  "mongodb.uri" -> "mongodb://localhost:27017/Test-email-verification")) extends ServiceSpec with ScalaFutures with IntegrationPatience with WireMockHelper  with BeforeAndAfterEach {
  override val server = new IntegrationServer(getClass.getSimpleName,  extraConfig)

  def appClient(path: String) = WS.url(resource(s"/email-verification$path"))

  lazy val tokenRepo = VerificationTokenMongoRepository()
  lazy val verifiedRepo = VerifiedEmailMongoRepository()

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
