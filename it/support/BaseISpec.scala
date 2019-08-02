package support

import com.typesafe.config.Config
import org.scalatest.{GivenWhenThen, Matchers, WordSpec}
import play.api.{Configuration, Logger}
import play.api.test.WsTestClient
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.integration.ServiceSpec
import uk.gov.hmrc.integration.servicemanager.ServiceManagerClient
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import support.EmailStub._

class BaseISpec(val testConfig : Map[String, _ <: Any] = Map.empty) extends WordSpec with ServiceSpec with WsTestClient with Matchers with GivenWhenThen with MongoSpecSupport with WireMockHelper {
  implicit val timeout : Duration = 5 minutes

  override val externalServices:Seq[String] = Seq()

  def await[A](future: Future[A])(implicit timeout: Duration): A = Await.result(future, timeout)

  override val additionalConfig : Map[String, _ <: Any] = Map(
    "microservice.services.email.port" -> WireMockConfig.stubPort.toString,
    "microservice.services.platform-analytics.port" -> WireMockConfig.stubPort.toString,
    "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
    "mongodb.uri" -> mongoUri
  ) ++ testConfig

  implicit val config:Config = Configuration((additionalConfig).toSeq:_*).underlying

  lazy val mongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  def appClient(path: String) = resource(s"/email-verification$path")

    def tokenFor(email: String) = {
      stubSendEmailRequest(202)
      withClient { ws =>
        await(ws.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify = email))).status shouldBe 201
        decryptedToken(lastVerificationEMail)._1.get
      }
    }

  private lazy val tokenRepo = new VerificationTokenMongoRepository(mongoComponent)
  private lazy val verifiedRepo = new VerifiedEmailMongoRepository(mongoComponent)

  override def beforeEach() {
    await(tokenRepo.drop)
    await(verifiedRepo.drop)
    await(verifiedRepo.ensureIndexes)
    AnalyticsStub.stubAnalyticsEvent()
  }

  override def afterAll() {
    await(tokenRepo.drop)
    await(verifiedRepo.drop)

    Logger.debug(s"Stopping all external services")
    try {
      ServiceManagerClient.stop(testId, dropDatabases = true)
    } catch {
      case t: Throwable => if (t.getMessage == "Connection refused: localhost/0:0:0:0:0:0:0:1:8085")
        Logger.warn("smserver not running")
      else
        Logger.error(s"An exception occurred while stopping external services", t)
    }
  }


}
