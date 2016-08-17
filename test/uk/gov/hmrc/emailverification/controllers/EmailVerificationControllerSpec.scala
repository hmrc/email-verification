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

package uk.gov.hmrc.emailverification.controllers

import org.joda.time.Duration
import org.joda.time.format.{ISOPeriodFormat, PeriodFormatter, PeriodFormatterBuilder}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.emailverification.controllers.controllers.EmailVerificationController
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}


class EmailVerificationControllerSpec extends UnitSpec with WithFakeApplication {

  "requestVerification" should {
    "return 204" in {
      val validRequest = Json.parse("""{
                                      |  "email": "example@domain.com",
                                      |  "templateId": "my-lovely-template",
                                      |  "templateParameters": {
                                      |    "name": "Mr Joe Bloggs"
                                      |  },
                                      |  "linkExpiryDuration" : "P2D",
                                      |  "continueUrl" : "http://some/url"
                                      |}""".stripMargin
      )
      val result = EmailVerificationController.requestVerification()(FakeRequest().withBody(validRequest))
      status(result) shouldBe Status.NO_CONTENT
    }
  }


}
