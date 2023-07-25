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

package uk.gov.hmrc.emailverification.utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.emailverification.models.{Label, Labels, VerifyEmailRequest}
import uk.gov.hmrc.emailverification.utils.JourneyLabelsUtil.getTeamNameLabel

class JourneyLabelsUtilSpec extends AnyWordSpec with Matchers {

  "getTeamNameLabel" should {
    "return the Welsh team name when Welsh label exist" in {
      val labels = Some(Labels(cy = Label(null, Some("Welsh Team Name")), en = Label(null, Some("English Team Name"))))
      val request = VerifyEmailRequest(null, null, "origin", Some("DeskPro Name"), null, null, null, null, null, labels)

      getTeamNameLabel(request) shouldBe "Welsh Team Name"
    }

    "return the English team name when Welsh does not exist but English" in {
      val labels = Some(Labels(cy = Label(null, None), en = Label(null, Some("English Team Name"))))
      val request = VerifyEmailRequest(null, null, "origin", Some("DeskPro Name"), null, null, null, null, null, labels)

      getTeamNameLabel(request) shouldBe "English Team Name"
    }

    "return the DeskPro name when Welsh nor English does not exist but DeskPro" in {
      val labels = Some(Labels(cy = Label(null, None), en = Label(null, None)))
      val request = VerifyEmailRequest(null, null, "origin", Some("DeskPro Name"), null, null, null, null, null, labels)

      getTeamNameLabel(request) shouldBe "DeskPro Name"
    }

    "return the origin when Welsh, English nor DeskPro does not exist" in {
      val labels = Some(Labels(cy = Label(null, None), en = Label(null, None)))
      val request = VerifyEmailRequest(null, null, "origin", None, null, null, null, null, null, labels)

      getTeamNameLabel(request) shouldBe "origin"
    }

    "return the DeskPro Name when the labels field does not exist" in {
      val request = VerifyEmailRequest(null, null, "origin", Some("DeskPro Name"), null, null, null, null, null, None)

      getTeamNameLabel(request) shouldBe "DeskPro Name"
    }
  }
}
