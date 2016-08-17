/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.emailverification.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpPost}

trait EmailConnector {
  val serviceUrl: String
  def http: HttpPost

  def sendEmail(to: String, templateId: String, params: (String, String)*)(implicit hc: HeaderCarrier) = post(SendEmailRequest(Seq(to), templateId, params.toMap))

  private def post(payload: SendEmailRequest)(implicit hc: HeaderCarrier) = http.POST(s"$serviceUrl/send-templated-email", payload)
}

object EmailConnector extends EmailConnector with ServicesConfig {
  lazy val serviceUrl = baseUrl("email")
  override def http = WSHttp
}


case class SendEmailRequest(to: Seq[String], templateId: String, parameters: Map[String, String])

object SendEmailRequest {
  implicit val sendEmailRequestFmt: Writes[SendEmailRequest] = Json.writes[SendEmailRequest]
}
