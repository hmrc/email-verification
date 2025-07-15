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

import uk.gov.hmrc.emailverification.models.{VerifyEmailRequest, Welsh}

object JourneyLabelsUtil {

  def getTeamNameLabel(verifyEmailRequest: VerifyEmailRequest): String = {

    val labels = verifyEmailRequest.labels
    val deskPro = verifyEmailRequest.deskproServiceName
    val origin = verifyEmailRequest.origin

    val message = (verifyEmailRequest.lang, labels.map(_.en), labels.map(_.cy)) match {
      case (Some(Welsh), _, Some(cy)) => cy.userFacingServiceName
      case (_, None, Some(cy))        => cy.userFacingServiceName
      case (_, en, _)                 => en.flatMap(_.userFacingServiceName)
    }

    message orElse deskPro getOrElse origin
  }

  def getPageTitleLabel(verifyEmailRequest: VerifyEmailRequest): Option[String] = {
    val labels = verifyEmailRequest.labels
    val maybePageTitle = verifyEmailRequest.pageTitle

    val message = (verifyEmailRequest.lang, labels.map(_.en), labels.map(_.cy)) match {
      case (Some(Welsh), _, Some(cy)) => cy.pageTitle
      case (_, None, Some(cy))        => cy.pageTitle
      case (_, en, _)                 => en.flatMap(_.pageTitle)
    }

    message orElse maybePageTitle
  }
}
