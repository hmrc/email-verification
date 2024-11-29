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

package uk.gov.hmrc.emailverification.services

import config.AppConfig
import play.api.Logging
import uk.gov.hmrc.emailverification.models._
import uk.gov.hmrc.emailverification.repositories.VerificationCodeV2MongoRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EmailVerificationV2Service @Inject() (
  verificationCodeGenerator: PasscodeGenerator,
  verificationCodeRepository: VerificationCodeV2MongoRepository,
  emailService: EmailService,
  auditService: AuditV2Service
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends Logging {
  def doSendCode(sendCodeRequest: SendCodeV2Request)(implicit headerCarrier: HeaderCarrier): Future[SendCodeResult] = {
    if (!sendCodeRequest.isEmailValid)
      Future.successful(SendCodeResult.codeNotSent("Invalid email"))
    else
      for {
        verificationCode <- Future.successful(verificationCodeGenerator.generate())
        status           <- emailService.sendCode(sendCodeRequest.email, verificationCode, appConfig.appName, English)
        _ <- verificationCodeRepository.put(sendCodeRequest.email)(
               VerificationCodeV2MongoRepository.emailVerificationCodeDataDataKey,
               VerificationCodeMongoDoc(sendCodeRequest.email, verificationCode)
             )
        _ <- auditService.sendVerificationCode(sendCodeRequest.email, verificationCode, appConfig.appName, status)
      } yield status
  }

  def doVerifyCode(verifyCodeRequest: VerifyCodeV2Request)(implicit headerCarrier: HeaderCarrier): Future[VerifyCodeResult] = {
    (verifyCodeRequest.isEmailValid, verifyCodeRequest.isVerificationCodeValid) match {
      case (false, _) => Future.successful(VerifyCodeResult.codeNotValid(Some("Invalid email")))
      case (_, false) => Future.successful(VerifyCodeResult.codeNotValid(Some("Invalid verification code")))
      case (true, true) =>
        for {
          doc <- verificationCodeRepository.get(verifyCodeRequest.email)(VerificationCodeV2MongoRepository.emailVerificationCodeDataDataKey)
          verified = doc.map(_.verificationCode == verifyCodeRequest.verificationCode)
          verifiedStatus = if (verified.isEmpty) VerifyCodeResult.codeNotFound("Verification code not found")
                           else if (verified.getOrElse(false)) VerifyCodeResult.codeVerified()
                           else VerifyCodeResult.codeNotVerified("Invalid verification code")
          _ <- auditService.verifyVerificationCode(verifyCodeRequest.email, verifyCodeRequest.verificationCode, appConfig.appName, verifiedStatus)
        } yield verifiedStatus
    }
  }

  def getVerificationCode(sendCodeRequest: SendCodeV2Request): Future[Option[String]] =
    for {
      maybeDoc <- verificationCodeRepository.get(sendCodeRequest.email)(VerificationCodeV2MongoRepository.emailVerificationCodeDataDataKey)
    } yield maybeDoc.map(_.verificationCode)
}
