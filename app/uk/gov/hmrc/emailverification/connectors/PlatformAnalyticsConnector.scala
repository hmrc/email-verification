/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.emailverification.connectors

import config.{HttpClient, WSHttpClient}
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

case class DimensionValue(index: String, value: String)

case class GaEvent(category: String, action: String, label: String, dimensions: Seq[DimensionValue], userId: Option[String])

object GaEvent {
  implicit val dimensionWrites: Writes[DimensionValue] = Json.writes[DimensionValue]
  implicit val eventWrites: Writes[GaEvent] = Json.writes[GaEvent]
}

case class AnalyticsRequest(gaClientId: String, events: Seq[GaEvent])

object AnalyticsRequest {
  implicit val writes: Writes[AnalyticsRequest] = Json.writes[AnalyticsRequest]
}

object GaEvents {
  val verificationRequested = GaEvent("sos_email_verification", "verification_requested", "verification_sent", Seq.empty, None)
  val verificationSuccess = GaEvent("sos_email_verification", "verification_link_actioned", "success", Seq.empty, None)
  val verificationFailed = GaEvent("sos_email_verification", "verification_link_actioned", "failure", Seq.empty, None)
}

trait PlatformAnalyticsConnector {
  def serviceUrl: String
  def httpClient: HttpClient
  def gaClientId: String

  def sendEvents(events: GaEvent*)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = sendEvents(AnalyticsRequest(gaClientId, events))

  private def sendEvents(data: AnalyticsRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    val url = s"$serviceUrl/platform-analytics/event"
    httpClient.POST(url, data, Seq.empty).map(_ => ()).recoverWith {
      case e: Exception =>
        Logger.error(s"Couldn't send analytics event $data", e)
        Future.successful(())
    }
  }
}

object PlatformAnalyticsConnector extends PlatformAnalyticsConnector with ServicesConfig {
  override lazy val serviceUrl = baseUrl("platform-analytics")
  override lazy val httpClient = WSHttpClient
  override def gaClientId = s"GA1.1.${Math.abs(Random.nextInt())}.${Math.abs(Random.nextInt())}"
}
