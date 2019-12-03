package support

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.mockito.MockitoSugar

object AnalyticsStub extends MockitoSugar {
  private val analyticsMatchingStrategy = urlEqualTo("/platform-analytics/event")

  def stubAnalyticsEvent(): Unit =
    stubFor(post(analyticsMatchingStrategy).willReturn(aResponse()
      .withStatus(200)
      .withBody("ok")))

}
