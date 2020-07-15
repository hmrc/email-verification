/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.emailverification

import javax.inject.Inject
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.emailverification.models.ErrorResponse
import uk.gov.hmrc.http.{HeaderCarrier, JsValidationException, NotFoundException, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import uk.gov.hmrc.play.bootstrap.http.JsonErrorHandler

import scala.concurrent.{ExecutionContext, Future}

class ErrorHandlerOverride @Inject()(configuration: Configuration, auditConnector: AuditConnector, httpAuditEvent: HttpAuditEvent)(implicit ec: ExecutionContext) extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration) {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {

    implicit val headerCarrier: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSessionAndRequest(request.headers, request = Some(request))

    statusCode match {
      case play.mvc.Http.Status.NOT_FOUND =>
        auditConnector.sendEvent(httpAuditEvent.dataEvent("ResourceNotFound", "Resource Endpoint Not Found", request))
        Future.successful(
          NotFound(Json.toJson(ErrorResponse("NOT_FOUND", "URI not found", Some(Map("requestedUrl" → request.path)))))
        )
      case _ ⇒ super.onClientError(request, statusCode, message)
    }
  }

  override def onServerError(request: RequestHeader, ex: Throwable): Future[Result] = {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSessionAndRequest(request.headers, request = Some(request))

    Logger.error(s"! Internal server error, for (${request.method}) [${request.uri}] -> ", ex)

    val code = ex match {
      case _: NotFoundException => "ResourceNotFound"
      case _: AuthorisationException => "ClientError"
      case _: JsValidationException => "ServerValidationError"
      case _ => "ServerInternalError"
    }

    auditConnector.sendEvent(
      httpAuditEvent.dataEvent(code, "Unexpected error", request, Map("transactionFailureReason" -> ex.getMessage)))
    Future.successful(resolveError(ex))
  }

  private def resolveError(ex: Throwable): Result = {
    val (statusCode, code, message) = ex match {
      case e: Upstream4xxResponse => (BAD_GATEWAY, "UPSTREAM_ERROR", e.getMessage)
      case e: NotFoundException => (BAD_GATEWAY, "UPSTREAM_ERROR", e.getMessage)
      case e: Upstream5xxResponse => (BAD_GATEWAY, "UPSTREAM_ERROR", e.getMessage)
      case e: Throwable => (INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR", e.getMessage)
    }

    new Status(statusCode)(Json.toJson(ErrorResponse(code, message)))
  }

}
