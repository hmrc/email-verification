package support

import _root_.play.api.libs.json.{JsValue, Json}
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.ShouldMatchers
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.crypto.Crypted.fromBase64
import uk.gov.hmrc.crypto.CryptoWithKeysFromConfig

import scala.collection.JavaConverters._

object AnalyticsStub extends MockitoSugar with ShouldMatchers {
  private val emailMatchingStrategy = urlEqualTo("/platform-analytics/event")

  def stubAnalyticsEvent() =
    stubFor(post(emailMatchingStrategy).willReturn(aResponse()
      .withStatus(200)
      .withBody("ok")))

}
