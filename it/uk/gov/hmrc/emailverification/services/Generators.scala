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

import org.scalacheck.Gen

trait Generators {
  val emails: Gen[String] = for {
    user <- Gen.alphaNumStr if user.nonEmpty
    domain <- Gen.alphaStr if domain.nonEmpty
    suffix <- Gen.listOfN(2, Gen.alphaChar)
  } yield s"$user@$domain.${suffix.mkString}"
}
