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

import play.api.libs.json.{Format, Json, Reads}

case class VerifyEmailRequest(
  credId: String,
  continueUrl: String,
  origin: String,
  deskproServiceName: Option[String],
  accessibilityStatementUrl: String,
  email: Option[Email],
  lang: Option[Language],
  backUrl: Option[String],
  pageTitle: Option[String],
  labels: Option[Labels]
)

case class Email(address: String, enterUrl: String)

case class Label(pageTitle: Option[String], userFacingServiceName: Option[String])
case class Labels(en: Label, cy: Label)

object Email {
  implicit val format: Format[Email] = Json.format
}

object VerifyEmailRequest {
  implicit val reads: Reads[VerifyEmailRequest] = Json.reads
}

object Label {
  implicit val reads: Reads[Label] = Json.reads
}

object Labels {
  implicit val reads: Reads[Labels] = Json.reads
}
