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

import play.api.libs.json._
import play.api.mvc.{ControllerComponents, Request, Result}
import uk.gov.hmrc.emailverification.models.ErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

abstract class BaseControllerWithJsonErrorHandling @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  private val separatorChar: String = ";"

  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], ct: ClassTag[T], reads: Reads[T]): Future[Result] =
    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs)) =>
        val details = errs.map { case (jsPath, errors) => jsPath.toJsonString -> errors.map(_.message).mkString(separatorChar) }.toMap
        Future.successful(BadRequest(Json.toJson(ErrorResponse("VALIDATION_ERROR", "Payload validation failed", Some(details)))))
      case Failure(e) =>
        Future.successful(BadRequest(Json.toJson(ErrorResponse("VALIDATION_ERROR", e.getMessage))))
    }
}
