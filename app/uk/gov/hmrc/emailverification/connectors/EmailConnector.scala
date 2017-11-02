/*
 * Copyright 2017 HM Revenue & Customs
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

import config.{AppConfig, HttpClient, WSHttpClient}
import play.api.libs.json.Json
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

case class SendEmailRequest(to: Seq[String], templateId: String, parameters: Map[String, String])

object SendEmailRequest {
  implicit val writes = Json.writes[SendEmailRequest]
}

trait EmailConnector {
  def servicePath: String

  def baseServiceUrl: String

  def httpClient: HttpClient

  def sendEmail(to: String, templateId: String, params: Map[String, String])(implicit hc: HeaderCarrier, ec: ExecutionContext) =
    httpClient.POST(s"$baseServiceUrl$servicePath/hmrc/email", SendEmailRequest(Seq(to), templateId, params))
}

object EmailConnector extends EmailConnector with ServicesConfig {
  override lazy val servicePath = AppConfig.emailServicePath

  override lazy val baseServiceUrl = baseUrl("email")

  override lazy val httpClient = WSHttpClient

}
