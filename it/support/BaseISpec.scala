package support

import com.typesafe.config.Config
import org.scalatest.{GivenWhenThen, Matchers, WordSpec}
import play.api.Configuration
import play.api.test.WsTestClient
import play.modules.reactivemongo.ReactiveMongoComponent
import support.EmailStub._
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.gg.test.{UnitSpec, WireMockSpec}
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

abstract class BaseISpec(val testConfig : Map[String, _ <: Any] = Map.empty) extends WordSpec with UnitSpec with WsTestClient with Matchers with GivenWhenThen with MongoSpecSupport with WireMockSpec {
  implicit val timeout : Duration = 5.minutes

  def await[A](future: Future[A])(implicit timeout: Duration): A = Await.result(future, timeout)

  override val extraConfig : Map[String, _ <: Any] = Map(
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
    "microservice.services.email.port" -> WireMockConfig.stubPort.toString,
    "microservice.services.platform-analytics.port" -> WireMockConfig.stubPort.toString,
    "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
    "mongodb.uri" -> mongoUri
  ) ++ testConfig

  implicit val config:Config = Configuration(extraConfig.toSeq:_*).underlying

  lazy val mongoComponent: ReactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  def appClient(path: String): String = resource(s"/email-verification$path")

    def tokenFor(email: String): String = {
      expectEmailServiceToRespond(202)
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
  }
}
