package uk.gov.hmrc

import uk.gov.hmrc.play.it.MicroServiceEmbeddedServer

import scala.concurrent.duration._

class IntegrationServer(val testName: String) extends MicroServiceEmbeddedServer {

  override val externalServices = Seq.empty
  override def additionalConfig = Map.empty
  override protected def startTimeout = 180.seconds
}
