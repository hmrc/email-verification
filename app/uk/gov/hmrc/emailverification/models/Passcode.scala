package uk.gov.hmrc.emailverification.models

import play.api.libs.json.{Json, Reads}

case class Passcode(passcode: String) extends AnyVal

object Passcode {
  implicit val writes = Json.writes[Passcode]
}
