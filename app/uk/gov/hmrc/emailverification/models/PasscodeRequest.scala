/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.format.ISOPeriodFormat
import org.joda.time.Period
import play.api.libs.json.{JsPath, Json, Reads}

case class PasscodeRequest(sessionId:String,
                           email:String,
                           templateId: String,
                           templateParameters: Option[Map[String, String]],
                           linkExpiryDuration: Period)

object PasscodeRequest {
  implicit val periodReads: Reads[Period] = JsPath.read[String].map(ISOPeriodFormat.standard().parsePeriod)
  implicit def reads: Reads[PasscodeRequest] = Json.reads[PasscodeRequest]
}