package support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

object WireMockConfig {
  val stubPort = 11111
  val stubHost = "localhost"
}

trait WireMockHelper extends BeforeAndAfterAll with BeforeAndAfterEach {
  self: Suite =>
  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))
  val stubPort: Int = WireMockConfig.stubPort
  val stubHost: String = WireMockConfig.stubHost

  private def startMockServer(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  private def stopMockServer(): Unit = {
    wireMockServer.stop()
    wireMockServer.resetMappings()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    startMockServer()
  }

  override def afterAll(): Unit = {
    stopMockServer()
    super.afterAll()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    WireMock.reset()
  }
}
