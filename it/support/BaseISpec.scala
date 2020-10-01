package support

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{noContent, post, stubFor}
import com.typesafe.config.Config
import org.scalatest.GivenWhenThen
import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoComponent
import support.EmailStub._
import uk.gov.hmrc.emailverification.repositories.{VerificationTokenMongoRepository, VerifiedEmailMongoRepository}
import uk.gov.hmrc.gg.test.WireMockSpec
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import scala.concurrent.ExecutionContext.Implicits.global

trait BaseISpec extends WireMockSpec with MongoSpecSupport with GivenWhenThen {

  override def extraConfig: Map[String, Any] = Map(
    "play.http.router" -> "testOnlyDoNotUseInAppConf.Routes",
    "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
    "mongodb.uri" -> mongoUri
  )

  lazy val mongoComponent: ReactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  def appClient(path: String): String = resource(s"/email-verification$path")

  implicit val config: Config = Configuration.from(extraConfig).underlying

  def tokenFor(email: String): String = {
    expectEmailToBeSent()

    await(wsClient.url(appClient("/verification-requests")).post(verificationRequest(emailToVerify = email))).status shouldBe 201
    decryptedToken(lastVerificationEmail)._1.get
  }

  private lazy val tokenRepo = new VerificationTokenMongoRepository(mongoComponent)
  private lazy val verifiedRepo = new VerifiedEmailMongoRepository(mongoComponent)

  override def beforeEach(): Unit = {
    super.beforeEach()
    WireMock.reset()

    await(tokenRepo.drop)
    await(verifiedRepo.drop)
    await(verifiedRepo.ensureIndexes)
    AnalyticsStub.stubAnalyticsEvent()
    stubFor(post("/write/audit").willReturn(noContent))
    stubFor(post("/write/audit/merged").willReturn(noContent))
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(tokenRepo.drop)
    await(verifiedRepo.drop)
    dropTestCollection("passcode")
  }
}
