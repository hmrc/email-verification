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

import play.api.libs.json._
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

case class FieldError(fieldName: String, message: String)

object FieldError {
  implicit val writes = Json.writes[FieldError]
}

case class ErrorResponse(val code: String,
                         val message: String,
                         val errors: Seq[FieldError])

object ErrorResponse {
  implicit val writes = Json.writes[ErrorResponse]
}

trait BaseControllerWithJsonErrorHandling extends BaseController {

  override protected def withJsonBody[T](f: (T) => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] =
    request.body.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(errs) =>
        val fieldErrors = errs.flatMap {
          case (jsPath, errors) => errors.map(validationError => FieldError(jsPath.toJsonString, validationError.message))
        }
        Future.successful(BadRequest(Json.toJson(ErrorResponse("VALIDATION_ERROR", "Payload validation failed", fieldErrors))))
    }
}
