package support

import uk.gov.hmrc.play.it.MicroServiceEmbeddedServer

import scala.concurrent.duration._

class IntegrationServer(val testName: String, extraConfig: Map[String, String]) extends MicroServiceEmbeddedServer {

  override val externalServices = Seq.empty
  override def additionalConfig = extraConfig
  override val startTimeout = 180.seconds
}
