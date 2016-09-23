package support

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.ShouldMatchers
import org.scalatest.mock.MockitoSugar

object AnalyticsStub extends MockitoSugar with ShouldMatchers {
  private val analyticsMatchingStrategy = urlEqualTo("/platform-analytics/event")

  def stubAnalyticsEvent() =
    stubFor(post(analyticsMatchingStrategy).willReturn(aResponse()
      .withStatus(200)
      .withBody("ok")))

}
