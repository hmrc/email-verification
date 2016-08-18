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
  val stubPort = WireMockConfig.stubPort
  val stubHost = WireMockConfig.stubHost

  private def startMockServer() = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  private def stopMockServer() = {
    wireMockServer.stop()
    wireMockServer.resetMappings()
  }

  override def beforeAll() = {
    super.beforeAll()
    startMockServer()
  }

  override def afterAll() = {
    super.afterAll()
    stopMockServer()
  }

  override def beforeEach() = {
    super.beforeEach()
    WireMock.reset()
  }
}
