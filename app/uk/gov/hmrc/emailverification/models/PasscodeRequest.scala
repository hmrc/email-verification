package uk.gov.hmrc.emailverification.models

import org.joda.time.Period
import play.api.libs.json.{Json, Reads}

case class PasscodeRequest(sessionId:String,
                           email:String,
                           templateId: String,
                           templateParameters: Option[Map[String, String]],
                           linkExpiryDuration: Period)

object PasscodeRequest {
  implicit def reads: Reads[PasscodeRequest] = Json.reads[PasscodeRequest]
}