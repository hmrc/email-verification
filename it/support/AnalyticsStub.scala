package support

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.mockito.MockitoSugar

object AnalyticsStub extends MockitoSugar {
  def stubAnalyticsEvent(): Unit =
    stubFor(post(urlEqualTo("/platform-analytics/event"))
      .willReturn(ok()))

}
