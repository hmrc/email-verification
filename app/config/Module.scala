/*
 * Copyright 2024 HM Revenue & Customs
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

import com.google.inject.{AbstractModule, Provides}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.emailverification.repositories.VerificationCodeV2MongoRepository
import uk.gov.hmrc.emailverification.services._

import scala.concurrent.ExecutionContext

class Module(env: Environment, config: Configuration) extends AbstractModule {
  @Provides
  def emailVerificationServiceProvider(verificationCodeGenerator: PasscodeGenerator,
                                       verificationCodeRepository: VerificationCodeV2MongoRepository,
                                       emailService: EmailService,
                                       auditService: AuditV2Service
                                      )(implicit appConfig: AppConfig, ec: ExecutionContext): EmailVerificationV2Service = {
    if (appConfig.useTestEmailVerificationService)
      new TestEmailVerificationV2Service()
    else
      new LiveEmailVerificationV2Service(verificationCodeGenerator, verificationCodeRepository, emailService, auditService)
  }
}
