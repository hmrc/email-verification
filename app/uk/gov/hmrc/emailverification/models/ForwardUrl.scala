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

package uk.gov.hmrc.emailverification.models

import java.net.URI

import config.AppConfig
import play.api.libs.json._

import scala.util.Try

case class ForwardUrl(url: String)

object ForwardUrl {

  implicit def reads(implicit appConfig: AppConfig): Reads[ForwardUrl] = (json: JsValue) => {
    val url = json.as[String]
    validate(url, appConfig) match {
      case Left(message)     => JsError(error = message)
      case Right(forwardUrl) => JsSuccess(forwardUrl)
    }
  }

  implicit val writes: Writes[ForwardUrl] = (url: ForwardUrl) => JsString(url.url)

  private def validate(potentialUrl: String, appConfig: AppConfig): Either[String, ForwardUrl] = {
    Try(new URI(potentialUrl)).map {
      uri => Right(ForwardUrl(uri.toString))
    }.getOrElse(Left("URL could not be parsed"))
  }

}
