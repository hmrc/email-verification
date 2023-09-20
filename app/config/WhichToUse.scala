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

package config

sealed abstract class WhichToUse(val value: String)
object WhichToUse {
  case object New extends WhichToUse("new")
  case object Old extends WhichToUse("old")
  case object Both extends WhichToUse("both")
  def forCollectionToCheck(value: String): WhichToUse = value.toLowerCase match {
    case New.value  => New
    case Old.value  => Old
    case Both.value => Both
    case _          => throw new RuntimeException(s"Unsupported value '$value', should be 'old', 'new' or 'both'")
  }
  def forCollectionToUpdate(value: String): WhichToUse = value.toLowerCase match {
    case New.value  => New
    case Both.value => Both
    case _          => throw new RuntimeException(s"Unsupported value '$value', should be 'new' or 'both'")
  }
}
