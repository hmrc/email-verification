package support

import _root_.play.api.libs.json.{JsValue, Json}
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.typesafe.config.Config
import org.scalatest.{Assertion, Matchers}
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.crypto.Crypted.fromBase64
import uk.gov.hmrc.crypto.CryptoWithKeysFromConfig

import scala.collection.JavaConverters._

object EmailStub extends MockitoSugar with Matchers {
  private val emailMatchingStrategy = urlEqualTo("/hmrc/email")
  private val emailEventStub = postRequestedFor(emailMatchingStrategy)
  private def crypto(implicit config:Config) = new CryptoWithKeysFromConfig("queryParameter.encryption", config)

  def verificationRequest(emailToVerify: String = "test@example.com",
                          templateId: String = "some-template-id",
                          continueUrl: String = "http://example.com/continue",
                          paramsJsonStr: String = "{}"): JsValue =
    Json.parse(s"""{
                   |  "email": "$emailToVerify",
                   |  "templateId": "$templateId",
                   |  "templateParameters": $paramsJsonStr,
                   |  "linkExpiryDuration" : "P2D",
                   |  "continueUrl" : "$continueUrl"
                   |}""".stripMargin)

  def stubSendEmailRequest(status: Int, body: String): Unit =
    stubFor(post(emailMatchingStrategy).willReturn(aResponse()
      .withStatus(status)
      .withBody(body)))

  def stubSendEmailRequest(status: Int): Unit =
    stubFor(post(emailMatchingStrategy).willReturn(aResponse()
      .withStatus(status)))

  def verifyEmailSent(to: String, continueUrl: String, templateId: String, params: Map[String, String])(implicit config:Config): Assertion = {
    val emailSendRequestJson = lastVerificationEMail

    (emailSendRequestJson \ "to").as[Seq[String]] shouldBe Seq(to)
    (emailSendRequestJson \ "templateId").as[String] shouldBe templateId

    val (token, continueUrl) = decryptedToken(emailSendRequestJson)

    continueUrl shouldBe continueUrl
    token.isDefined shouldBe true
  }

  def decryptedToken(emailSendRequestJson: JsValue) (implicit config:Config): (Option[String], String) = {
    val verificationLink = (emailSendRequestJson \ "parameters" \ "verificationLink").as[String]

    val decryptedTokenJson = decryptToJson(verificationLink.split("token=")(1))
    ((decryptedTokenJson \ "token").asOpt[String], (decryptedTokenJson \ "continueUrl").as[String])
  }

  def lastVerificationEMail: JsValue = {
    val emails = WireMock.findAll(emailEventStub).asScala
    val emailSendRequest = emails.last.getBodyAsString
    Json.parse(emailSendRequest)
  }

  def decryptToJson(encrypted: String)(implicit config: Config): JsValue = {
    val base64DecodedEncrypted = fromBase64(encrypted)
    val decrypted = crypto.decrypt(base64DecodedEncrypted).value
    Json.parse(decrypted)
  }
}
