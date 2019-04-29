package support

import com.typesafe.config.Config
import play.api.Configuration
import play.api.Play.current
import play.api.libs.ws.WS
import play.modules.reactivemongo.ReactiveMongoComponent
import support.EmailStub._
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.it.ServiceSpec

import scala.concurrent.ExecutionContext.Implicits.global

class IntegrationBaseSpec(additionalConfig: Map[String, String] = Map.empty) extends ServiceSpec
    with WireMockHelper
    with MongoSpecSupport
{

  private val baseConfig: Map[String, String] = Map(
    "microservice.services.email.port" -> WireMockConfig.stubPort.toString,
    "microservice.services.platform-analytics.port" -> WireMockConfig.stubPort.toString,
    "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
    "mongodb.uri" -> mongoUri
  )

  override val server = new IntegrationServer(getClass.getSimpleName, baseConfig ++ additionalConfig)

  def appClient(path: String) = WS.url(resource(s"/email-verification$path"))

  lazy val mongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private lazy val tokenRepo = new VerificationTokenMongoRepository(mongoComponent)
  private lazy val verifiedRepo = new VerifiedEmailMongoRepository(mongoComponent)

  implicit val config:Config = Configuration((baseConfig ++ additionalConfig).toSeq:_*).underlying

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
