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
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.emailverification.models.SendEmailRequest
import uk.gov.hmrc.gg.test.{UnitSpec, WiremockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext

class EmailConnectorISpec extends UnitSpec with ScalaFutures with GuiceOneServerPerSuite with WiremockSupport {

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure(
      replaceExternalDependenciesWithMockServers ++ Seq(
        "microservice.services.email.host" -> wiremockHost,
        "microservice.services.email.port" -> wiremockPort
      )
    )
    .build()

  "send email" should {

    "submit request to email micro service to send email and get successful response status" in {
      val executionContext: ExecutionContext = ExecutionContext.Implicits.global
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
      val result: HttpResponse = await(connector.sendEmail(recipient, templateId, params)(HeaderCarrier(), executionContext))

      // then
      result.status shouldBe 202
    }
  }
}
