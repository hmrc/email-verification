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

import play.api.libs.json._

sealed trait Language {
  val value: String
}
case object English extends Language {
  override val value: String = "en"
}
case object Welsh extends Language {
  override val value: String = "cy"
}

object Language {
  implicit val reads: Reads[Language] = (json: JsValue) => {
    json.validate[String].map(_.toLowerCase).flatMap {
      case English.value => JsSuccess(English)
      case Welsh.value   => JsSuccess(Welsh)
      case other         => JsError(s"invalid language $other")
    }
  }

  implicit val writes: Writes[Language] = {
    case English => JsString("en")
    case Welsh   => JsString("cy")
  }
}

case class PasscodeRequest(email: String, serviceName: String, lang: Language)

object PasscodeRequest {
  implicit def reads: Reads[PasscodeRequest] = Json.reads[PasscodeRequest]
}
