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

package uk.gov.hmrc.emailverification.controllers

import config.AppConfig
import org.slf4j.LoggerFactory
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc._

import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
trait AccessChecker {
  this: BaseController =>

  val config: AppConfig

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val accessRequestFormUrl: String = config.accessRequestFormUrl
  private val checkAllowList: Boolean = config.accessControlEnabled
  private val allowedClients: Set[String] = config.accessControlAllowList

  private def areClientsAllowed(clients: Seq[String]): Boolean =
    clients.forall(allowedClients.contains)

  private def forbiddenResponse(clients: Seq[String]): String =
    s"""{
       |"code": $FORBIDDEN,
       |"description": "One or more user agents in '${clients.mkString(
        ","
      )}' are not authorized to use this service. Please complete '$accessRequestFormUrl' to request access."
       |}""".stripMargin

  private def getClientsFromRequest[T](req: Request[T]): Seq[String] = {
    val originator = req.headers.get("OriginatorId")

    if (originator.isDefined) {
      Seq(originator.get)
    } else {
      req.headers.getAll(HeaderNames.USER_AGENT).flatMap(_.split(","))
    }
  }

  def accessCheckedAction[A](
    bodyParser: BodyParser[A]
  )(block: Request[A] => Future[Result]): Action[A] = {
    Action.async(bodyParser) { request =>
      val callingClients = getClientsFromRequest(request)
      if (!areClientsAllowed(callingClients)) {
        if (checkAllowList) {
          Future.successful(
            Forbidden(Json.parse(forbiddenResponse(callingClients)))
          )
        } else {
          logger.warn(
            s"One or more user agents in '${callingClients.mkString(",")}' are not authorized to use this service"
          )
          block(request)
        }
      } else {
        block(request)
      }
    }
  }
}
