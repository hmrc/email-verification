/*
 * Copyright 2017 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Writes
import uk.gov.hmrc.emailverification.MockitoSugarRush
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class EmailConnectorSpec extends UnitSpec with MockitoSugarRush with ScalaFutures {

  "send email" should {

    "submit request to email micro service to send email and get successful response status" in new Setup {

      // given
      val endpointUrl = s"${url + path}/hmrc/email"
      when(httpMock.POST[SendEmailRequest, HttpResponse]
        (
          url = eqTo(endpointUrl),
          body = any[SendEmailRequest],
          headers = any[Seq[(String, String)]]())
        (
          wts = any[Writes[SendEmailRequest]](),
          rds = any[HttpReads[HttpResponse]](),
          hc = any[HeaderCarrier])
      ).thenReturn(Future.successful(HttpResponse(202)))

      // when
      val result = await(connector.sendEmail(recipient, templateId, params))

      // then
      result.status shouldBe 202

      // and
      val expectedResponseBody = SendEmailRequest(Seq(recipient), templateId, params)
      verify(httpMock).POST(
        url = eqTo(endpointUrl),
        body = eqTo(expectedResponseBody),
        headers = any[Seq[(String, String)]]())(
        wts = any[Writes[SendEmailRequest]](),
        rds = any[HttpReads[HttpResponse]](),
        hc = eqTo(hc))
    }
  }

  sealed trait Setup {
    implicit val hc = HeaderCarrier()
    val httpMock: WSHttp = mock[WSHttp]
    val path = "/some-path"
    val url = "http://somewhere"
    val params = Map("p1" -> "v1")
    val templateId = "my-template"
    val recipient = "user@example.com"

    val connector = new EmailConnector {
      override val servicePath = path
      override val http = httpMock
      override val baseServiceUrl = url
    }
  }

}
