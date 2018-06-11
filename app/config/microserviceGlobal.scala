/*
 * Copyright 2018 HM Revenue & Customs
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

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api._
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import uk.gov.hmrc.emailverification.controllers.ErrorResponse
import uk.gov.hmrc.http.{NotFoundException, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}

import scala.concurrent.Future

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
  lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport{
  override val auditConnector = MicroserviceAuditConnector

  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport{
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceAuthFilter extends AuthorisationFilter with MicroserviceFilterSupport{
  override lazy val authParamsConfig = AuthParamsControllerConfiguration
  override lazy val authConnector = MicroserviceAuthConnector

  override def controllerNeedsAuth(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuth
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = Some(MicroserviceAuthFilter)

  override def onError(request: RequestHeader, ex: Throwable) = {
    val (statusCode, code, message) = ex match {
      case e: Upstream4xxResponse => (BAD_GATEWAY, "UPSTREAM_ERROR", e.getMessage)
      case e: NotFoundException => (BAD_GATEWAY, "UPSTREAM_ERROR", e.getMessage)
      case e: Upstream5xxResponse => (BAD_GATEWAY, "UPSTREAM_ERROR", e.getMessage)
      case e: Throwable => (INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR", e.getMessage)
    }
    Future.successful(new Status(statusCode)(Json.toJson(ErrorResponse(code, message))))
  }

  override def onHandlerNotFound(request: RequestHeader) =
    Future.successful(NotFound(Json.toJson(ErrorResponse("NOT_FOUND", s"URI not found", Some(Map("requestedUrl" -> request.path))))))

  override def onBadRequest(request: RequestHeader, error: String) =
    Future.successful(BadRequest(Json.toJson(ErrorResponse("BAD_REQUEST", error))))

}
