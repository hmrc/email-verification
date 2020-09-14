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

import org.joda.time.DateTime
import play.api.libs.json.{Format, JsObject, JsResult, JsValue, Json, OFormat, OWrites, Reads}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class PasscodeDoc(sessionId:String, email: String, passcode: String, expireAt: DateTime, passcodeAttemps:Int=0, emailAttempts:Int=0)

object PasscodeDoc {
  implicit val dateTimeFormats: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val reads = new Reads[PasscodeDoc]() {
    override def reads(json: JsValue): JsResult[PasscodeDoc] = Json.reads[PasscodeDoc].reads(json)
  }
  implicit val writes = new OWrites[PasscodeDoc]() {
    override def writes(passcodeDoc: PasscodeDoc): JsObject = {
      Json.obj(
        "sessionID" -> passcodeDoc.sessionId,
        "email" -> passcodeDoc.email,
        "passcode" -> passcodeDoc.passcode,
        "passcodeAttempts"->passcodeDoc.passcodeAttemps,
        "$inc"-> Json.obj("emailAttempts"->1)
      )
    }
  }
  implicit val format = new OFormat[PasscodeDoc]() {
    override def reads(json: JsValue): JsResult[PasscodeDoc] = reads(json)
    override def writes(passcodeDoc: PasscodeDoc): JsObject = writes(passcodeDoc)
  }

}