/*
 * Copyright 2021 HM Revenue & Customs
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

package support

import _root_.play.api.libs.json.{JsValue, Json}
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.typesafe.config.Config
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import play.api.http.Status
import uk.gov.hmrc.crypto.Crypted.fromBase64
import uk.gov.hmrc.crypto.CryptoWithKeysFromConfig

import scala.collection.JavaConverters._

object EmailStub extends Matchers {
  private def crypto(implicit config:Config) = new CryptoWithKeysFromConfig("queryParameter.encryption", config)

  def verificationRequest(emailToVerify: String = "test@example.com",
                          templateId: String = "some-template-id",
                          continueUrl: String = "http://example.com/continue"): JsValue =
    Json.parse(s"""{
                  |  "email": "$emailToVerify",
                  |  "templateId": "$templateId",
                  |  "linkExpiryDuration" : "P2D",
                  |  "continueUrl" : "$continueUrl"
                  |}""".stripMargin)

  def passcodeRequest(email: String, lang: String = "en"): JsValue =
    Json.parse(s"""{
                  |  "email": "$email",
                  |  "serviceName": "apple",
                  |  "lang": "$lang"
                  |}""".stripMargin)

  def passcodeVerificationRequest(email: String, passcode: String ): JsValue =
    Json.parse(s"""{"passcode": "$passcode", "email": "$email"}""".stripMargin)

  def expectEmailServiceToRespond(status: Int, body: String): Unit =
    stubFor(post(urlEqualTo("/hmrc/email")).willReturn(aResponse()
      .withStatus(status)
      .withBody(body)))

  def expectEmailToBeSent(): Unit =
    stubFor(post(urlEqualTo("/hmrc/email")).willReturn(aResponse().withStatus(Status.ACCEPTED)))

  def verifyEmailSentWithContinueUrl(to: String, continueUrl: String, templateId: String)(implicit config:Config): Assertion = {
    val emailSendRequestJson = lastVerificationEmail

    (emailSendRequestJson \ "to").as[Seq[String]] shouldBe Seq(to)
    (emailSendRequestJson \ "templateId").as[String] shouldBe templateId

    val (token, decryptedContinueUrl) = decryptedToken(emailSendRequestJson)

    decryptedContinueUrl shouldBe continueUrl
    token.isDefined shouldBe true
  }

  def verifyEmailSentWithPasscode(to: String, templateId: String = "email_verification_passcode"): Assertion = {
    val emailSendRequestJson = lastVerificationEmail
    (emailSendRequestJson \ "to").as[Seq[String]] shouldBe Seq(to)
    (emailSendRequestJson \ "templateId").as[String] shouldBe templateId
    (emailSendRequestJson \ "parameters" \ "passcode").as[String] should fullyMatch regex "[BCDFGHJKLMNPQRSTVWXYZ]{6}"
  }

  def decryptedToken(emailSendRequestJson: JsValue) (implicit config:Config): (Option[String], String) = {
    val verificationLink = (emailSendRequestJson \ "parameters" \ "verificationLink").as[String]

    val decryptedTokenJson = decryptToJson(verificationLink.split("token=")(1))
    ((decryptedTokenJson \ "token").asOpt[String], (decryptedTokenJson \ "continueUrl").as[String])
  }

  def lastVerificationEmail: JsValue = {
    val emails = WireMock.findAll(postRequestedFor(urlEqualTo("/hmrc/email"))).asScala
    val emailSendRequest = emails.last.getBodyAsString
    Json.parse(emailSendRequest)
  }

  def verifyNoEmailSent = {
    WireMock.findAll(postRequestedFor(urlEqualTo("/hmrc/email"))) shouldBe empty
  }

  def lastPasscodeEmailed: String = {
    (lastVerificationEmail \ "parameters" \ "passcode").as[String]
  }

  def decryptToJson(encrypted: String)(implicit config: Config): JsValue = {
    val base64DecodedEncrypted = fromBase64(encrypted)
    val decrypted = crypto.decrypt(base64DecodedEncrypted).value
    Json.parse(decrypted)
  }
}
