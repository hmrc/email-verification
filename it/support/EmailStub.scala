package support

import java.util.UUID

import _root_.play.api.libs.json.Json
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

  def stubSendEmailRequest(status: Int, body: String) =
    stubFor(post(emailMatchingStrategy).willReturn(aResponse()
      .withStatus(status)
      .withBody(body)))

  def stubSendEmailRequest(status: Int) =
    stubFor(post(emailMatchingStrategy).willReturn(aResponse()
      .withStatus(status)))

  def verifyEmailSent(to: String, continueUrl: String, templateId: String, params: Map[String, String]): Unit = {
    val emailSendRequest = WireMock.findAll(emailEventStub).asScala.head.getBodyAsString
    val emailSendRequestJson = Json.parse(emailSendRequest)

    (emailSendRequestJson \ "to").as[Seq[String]] shouldBe Seq(to)
    (emailSendRequestJson \ "templateId").as[String] shouldBe templateId

    val verificationLink = (emailSendRequestJson \ "parameters" \ "verificationLink").as[String]

    val decryptedTokenJson = decryptToJson(verificationLink.split("token=")(1))

    (decryptedTokenJson \ "continueUrl").as[String] shouldBe continueUrl
    val token = (decryptedTokenJson \ "token").asOpt[String] map UUID.fromString
    token.isDefined shouldBe true
  }

  def decryptToJson(encrypted: String) = {
    val base64DecodedEncrypted = fromBase64(encrypted)
    val decrypted = crypto.decrypt(base64DecodedEncrypted).value
    Json.parse(decrypted)
  }
}
