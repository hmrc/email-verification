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

import uk.gov.hmrc.emailverification.services.PasscodeGenerator.codeSize

import scala.util.Random
import scala.util.matching.Regex

class PasscodeGenerator() {

  def generate(): String = {
    Random.alphanumeric
      .filterNot(_.isDigit)
      .filterNot(_.isLower)
      .filterNot(Set('A', 'E', 'I', 'O', 'U'))
      .take(codeSize)
      .mkString
  }
}

object PasscodeGenerator {
  val codeSize: Int = 6
  private val codePattern: Regex = ("\\p{Upper}" * codeSize).r

  def validate(code: String): Boolean = codePattern.matches(code)
}
