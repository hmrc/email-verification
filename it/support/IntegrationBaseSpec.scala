package support

import play.api.libs.ws.WS
import uk.gov.hmrc.play.it.ServiceSpec
import play.api.Play.current

class IntegrationBaseSpec(testName: String, extraConfig: Map[String, String] = Map.empty) extends ServiceSpec{
  private val testNameMaxLength = 30
  override val server = new IntegrationServer(testName.takeRight(testNameMaxLength),  extraConfig)

  def appClient(path: String) = WS.url(resource(s"/email-verification$path"))
}
