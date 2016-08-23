package support

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Play.current
import play.api.libs.ws.WS
import uk.gov.hmrc.play.it.ServiceSpec

class IntegrationBaseSpec(testName: String, extraConfig: Map[String, String] = Map.empty) extends ServiceSpec with ScalaFutures with IntegrationPatience {
  override val server = new IntegrationServer(testName,  extraConfig)

  def appClient(path: String) = WS.url(resource(s"/email-verification$path"))
}
