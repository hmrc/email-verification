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

import play.api.libs.json.{Format, Json}
import java.time.Instant

case class PasscodeDoc(sessionId: String, email: String, passcode: String, expireAt: Instant, passcodeAttempts: Int, emailAttempts: Int)

object PasscodeDoc {
  implicit val dateTimeFormats: Format[Instant] = uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.instantFormat
  implicit val format = Json.format[PasscodeDoc]
}
