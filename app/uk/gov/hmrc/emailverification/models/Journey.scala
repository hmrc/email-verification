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

import play.api.libs.json.{Format, Json, OFormat}

import java.time.Instant

case class Journey(
  journeyId: String,
  credId: String,
  continueUrl: String,
  origin: String,
  accessibilityStatementUrl: String,
  serviceName: String,
  language: Language,
  emailAddress: Option[String],
  enterEmailUrl: Option[String],
  backUrl: Option[String],
  pageTitle: Option[String],
  passcode: String,
  emailAddressAttempts: Int,
  passcodesSentToEmail: Int,
  passcodeAttempts: Int,
  labels: Option[Labels]
) {
  def frontendData: JourneyData = JourneyData(
    accessibilityStatementUrl,
    serviceName,
    enterEmailUrl,
    backUrl,
    pageTitle,
    emailAddress,
    labels
  )
}

object Journey {
  implicit val dateTimeFormats: Format[Instant] = uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.instantFormat
  implicit val format: OFormat[Journey] = Json.format[Journey]
}
