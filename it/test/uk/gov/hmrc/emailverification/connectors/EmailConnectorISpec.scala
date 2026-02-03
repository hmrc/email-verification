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

package uk.gov.hmrc.emailverification.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.IntegrationBaseSpec
import uk.gov.hmrc.emailverification.models.SendEmailRequest
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import scala.concurrent.ExecutionContext.global

class EmailConnectorISpec extends IntegrationBaseSpec with ScalaFutures  {

  "send email" should {

    "submit request to email micro service to send email and get successful response status" in {

      val params: Map[String, String] = Map("p1" -> "v1")
      val templateId = "my-template"
      val recipient = "user@example.com"

      val connector = app.injector.instanceOf[EmailConnector]

      // given
      val requestBody: SendEmailRequest = SendEmailRequest(Seq(recipient), templateId, params)

      stubFor(post("/hmrc/email")
        .withRequestBody(equalToJson(Json.toJson(requestBody).toString))
        .willReturn(aResponse().withStatus(202).withBody(""))
      )

      // when
      val result: HttpResponse = await(connector.sendEmail(recipient, templateId, params)(HeaderCarrier(), global))

      // then
      result.status shouldBe 202
    }
  }
}

class EmailConnectorStubISpec extends IntegrationBaseSpec with ScalaFutures  {

  override def serviceConfig: Map[String, Any] = {
    super.serviceConfig ++ Map(
      "microservice.services.email.path" -> "/email-verification-stub"
    )
  }

  "when the stub path is set" should {

    "send email" should {

      "submit request to email stub micro service to send email and get successful response status" in {

        val params: Map[String, String] = Map("p1" -> "v1")
        val templateId = "my-template"
        val recipient = "user@example.com"

        val connector = app.injector.instanceOf[EmailConnector]

        // given
        val requestBody: SendEmailRequest = SendEmailRequest(Seq(recipient), templateId, params)

        stubFor(post("/email-verification-stub/hmrc/email")
          .withRequestBody(equalToJson(Json.toJson(requestBody).toString))
          .willReturn(aResponse().withStatus(202).withBody(""))
        )

        // when
        val result: HttpResponse = await(connector.sendEmail(recipient, templateId, params)(HeaderCarrier(), global))

        // then
        result.status shouldBe 202
      }
    }
  }
}
