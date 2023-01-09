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

package uk.gov.hmrc.emailverification.services

import uk.gov.hmrc.gg.test.UnitSpec

class PasscodeGeneratorSpec extends UnitSpec {

  val passcodeGenerator = new PasscodeGenerator()

  "generate" should {
    "return a six character upper case code with no vowels" in {

      for {
        _ <- 1 to 106
      } yield {
        val passcode = passcodeGenerator.generate()
        passcode.toUpperCase() should fullyMatch regex "[^a-zAEIOU]{6}$"
      }

    }
  }

}
