package support

import _root_.play.api.libs.json.{JsValue, Json}
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.ShouldMatchers
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.crypto.Crypted.fromBase64
import uk.gov.hmrc.crypto.CryptoWithKeysFromConfig

import scala.collection.JavaConverters._

object EmailStub extends MockitoSugar with ShouldMatchers {
  private val emailMatchingStrategy = urlEqualTo("/send-templated-email")
  private val emailEventStub = postRequestedFor(emailMatchingStrategy)
  private lazy val crypto = CryptoWithKeysFromConfig("queryParameter.encryption")

  def verificationRequest(emailToVerify: String = "user@example.com",
                          templateId: String = "my-template",
                          continueUrl: String = "http://somewhere",
                          paramsJsonStr: String = "{}") =
    Json.parse(s"""{
                   |  "email": "$emailToVerify",
                   |  "templateId": "$templateId",
                   |  "templateParameters": $paramsJsonStr,
                   |  "linkExpiryDuration" : "P2D",
                   |  "continueUrl" : "$continueUrl"
                   |}""".stripMargin)

  def stubSendEmailRequest(status: Int, body: String) =
    stubFor(post(emailMatchingStrategy).willReturn(aResponse()
      .withStatus(status)
      .withBody(body)))

  def stubSendEmailRequest(status: Int) =
    stubFor(post(emailMatchingStrategy).willReturn(aResponse()
      .withStatus(status)))

  def verifyEmailSent(to: String, continueUrl: String, templateId: String, params: Map[String, String]): Unit = {
    val emailSendRequestJson = lastVerificationEMail

    (emailSendRequestJson \ "to").as[Seq[String]] shouldBe Seq(to)
    (emailSendRequestJson \ "templateId").as[String] shouldBe templateId

    val (token, continueUrl) = decryptedToken(emailSendRequestJson)

    continueUrl shouldBe continueUrl
    token.isDefined shouldBe true
  }

  def decryptedToken(emailSendRequestJson: JsValue) = {
    val verificationLink = (emailSendRequestJson \ "parameters" \ "verificationLink").as[String]

    val decryptedTokenJson = decryptToJson(verificationLink.split("token=")(1))
    ((decryptedTokenJson \ "token").asOpt[String], (decryptedTokenJson \ "continueUrl").as[String])
  }

  def lastVerificationEMail: JsValue = {
    val emails = WireMock.findAll(emailEventStub).asScala
    val emailSendRequest = emails.last.getBodyAsString
    Json.parse(emailSendRequest)
  }

  def decryptToJson(encrypted: String) = {
    val base64DecodedEncrypted = fromBase64(encrypted)
    val decrypted = crypto.decrypt(base64DecodedEncrypted).value
    Json.parse(decrypted)
  }
}
