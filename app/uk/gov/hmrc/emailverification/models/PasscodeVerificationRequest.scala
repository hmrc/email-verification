package uk.gov.hmrc.emailverification.models

import play.api.libs.json.{Json, Reads}

case class PasscodeVerificationRequest(sessionId:String, passcode:String)

object PasscodeVerificationRequest {
  implicit def reads: Reads[PasscodeVerificationRequest] = Json.reads[PasscodeVerificationRequest]
}