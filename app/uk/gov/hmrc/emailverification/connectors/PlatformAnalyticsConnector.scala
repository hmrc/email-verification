/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.Inject
import play.api.Mode.Mode
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.emailverification.models.{AnalyticsRequest, GaEvent}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


class PlatformAnalyticsConnector @Inject() (httpClient:HttpClient,
                                            environment: Environment,
                                            val runModeConfiguration:Configuration) extends ServicesConfig {

  val serviceUrl: String = baseUrl("platform-analytics")
  val gaClientId = s"GA1.1.${Math.abs(Random.nextInt())}.${Math.abs(Random.nextInt())}"

  def sendEvents(events: GaEvent*)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = sendEvents(AnalyticsRequest(gaClientId, events))

  private def sendEvents(data: AnalyticsRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    val url = s"$serviceUrl/platform-analytics/event"
    httpClient.POST(url, data, Seq.empty).map(_ => ()).recoverWith {
      case e: Exception =>
        Logger.error(s"Couldn't send analytics event $data", e)
        Future.successful(())
    }
  }
  override protected def mode: Mode = environment.mode

}
