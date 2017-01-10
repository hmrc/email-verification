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

import config.{AppConfig, WSHttp}
import play.api.libs.json.Json
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.ws.WSPost

case class SendEmailRequest(to: Seq[String], templateId: String, parameters: Map[String, String])

object SendEmailRequest {
  implicit val writes = Json.writes[SendEmailRequest]
}

trait EmailConnector {
  def config: AppConfig

  def serviceUrl: String

  def http: WSPost

  def sendEmail(to: String, templateId: String, params: Map[String, String])(implicit hc: HeaderCarrier) =
    http.POST(s"$serviceUrl${config.path}/send-templated-email", SendEmailRequest(Seq(to), templateId, params))
}

object EmailConnector extends EmailConnector with ServicesConfig {
  override lazy val config = AppConfig

  override lazy val serviceUrl = baseUrl("email")

  override lazy val http = WSHttp

}
