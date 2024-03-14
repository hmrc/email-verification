/*
 * Copyright 2023 HM Revenue & Customs
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

import config.AppConfig
import javax.inject.Inject
import uk.gov.hmrc.emailverification.models.SendEmailRequest
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

class EmailConnector @Inject() (
  appConfig: AppConfig,
  httpClient: HttpClient,
  servicesConfig: ServicesConfig
) {
  private lazy val servicePath: String = appConfig.emailServicePath

  private lazy val baseServiceUrl: String = servicesConfig.baseUrl("email")

  def sendEmail(to: String, templateId: String, params: Map[String, String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    httpClient
      .POST[SendEmailRequest, Either[UpstreamErrorResponse, HttpResponse]](s"$baseServiceUrl$servicePath/hmrc/email", SendEmailRequest(Seq(to), templateId, params))
      .map {
        case Left(err)    => throw err
        case Right(value) => value
      }
  }

}
