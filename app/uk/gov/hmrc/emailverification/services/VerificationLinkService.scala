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

import config.AppConfig
import play.api.libs.json.Json
import uk.gov.hmrc.crypto.{CryptoWithKeysFromConfig, PlainText}


case class VerificationToken(token: String, continueUrl: String)

object VerificationToken {
  implicit val writes = Json.writes[VerificationToken]
}

trait VerificationLinkService {
  def emailVerificationFrontendUrl: String
  def crypto: CryptoWithKeysFromConfig

  def verificationLinkFor(token: String, continueUrl: String) = s"$emailVerificationFrontendUrl/verification?token=${encryptedVerificationToken(token, continueUrl)}"

  private def encryptedVerificationToken(token: String, continueUrl: String) = {
    def encrypt(value: String) = new String(crypto.encrypt(PlainText(value)).toBase64)
    def tokenAsJson = Json.toJson(VerificationToken(token, continueUrl))
    encrypt(tokenAsJson.toString())
  }
}

object VerificationLinkService extends VerificationLinkService {
  override lazy val emailVerificationFrontendUrl = AppConfig.emailVerificationFrontendUrl
  override lazy val crypto = CryptoWithKeysFromConfig(baseConfigKey = "application.secret")
}
