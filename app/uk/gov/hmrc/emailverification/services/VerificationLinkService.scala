/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.emailverification.services

import java.util.UUID

import org.joda.time.DateTime
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.crypto.CryptoWithKeysFromConfig
import uk.gov.hmrc.emailverification.controllers.EmailVerificationRequest


case class VerificationToken(nonce: String, email: String, expiration: DateTime, continueUrl: String)

object VerificationToken {
  implicit val dateWrites = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  implicit val writes = Json.writes[VerificationToken]
}

trait VerificationLinkService {
  val emailVerificationFrontendUrl: String

  def createVerificationTokenValue(req: EmailVerificationRequest) = {
    val verificationData = VerificationToken(createNonce, req.email, currentTime.plus(req.linkExpiryDuration), req.continueUrl)
    Json.toJson(verificationData).toString()
  }

  def createNonce = UUID.randomUUID().toString

  def currentTime = DateTime.now()

  def crypto: CryptoWithKeysFromConfig
}

object VerificationLinkService extends VerificationLinkService {

  override val emailVerificationFrontendUrl: String = ???

  override def crypto = ???

}