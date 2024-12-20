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
import uk.gov.hmrc.emailverification.services.TestEmailVerificationV2Service.{getVerificationCodeFor, sendCodeResponseFor, verifyCodeResponseFor}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

sealed trait EmailVerificationV2Service {
  def doSendCode(sendCodeRequest: SendCodeV2Request)(implicit headerCarrier: HeaderCarrier): Future[SendCodeResult]
  def doVerifyCode(verifyCodeRequest: VerifyCodeV2Request)(implicit headerCarrier: HeaderCarrier): Future[VerifyCodeResult]
  def getVerificationCode(sendCodeRequest: SendCodeV2Request): Future[Option[String]]
}

class LiveEmailVerificationV2Service @Inject() (
  verificationCodeGenerator: PasscodeGenerator,
  verificationCodeRepository: VerificationCodeV2MongoRepository,
  emailService: EmailService,
  auditService: AuditV2Service
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends EmailVerificationV2Service
    with Logging {
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

//(implicit appConfig: AppConfig)
class TestEmailVerificationV2Service @Inject() () extends EmailVerificationV2Service with Logging {
  override def doSendCode(sendCodeRequest: SendCodeV2Request)(implicit headerCarrier: HeaderCarrier): Future[SendCodeResult] =
    Future.successful {
      sendCodeResponseFor(sendCodeRequest)
    }

  override def doVerifyCode(verifyCodeRequest: VerifyCodeV2Request)(implicit headerCarrier: HeaderCarrier): Future[VerifyCodeResult] =
    Future.successful(
      verifyCodeResponseFor(verifyCodeRequest)
    )

  override def getVerificationCode(sendCodeRequest: SendCodeV2Request): Future[Option[String]] =
    Future.successful(getVerificationCodeFor(sendCodeRequest.email))
}

object TestEmailVerificationV2Service {
//  private val sendCodeResponseMap = Map(
//    SendCodeV2Request("codesent@sendcode.com")    -> SendCodeResult.codeSent(),
//    SendCodeV2Request("codenotsent@sendcode.com") -> SendCodeResult.codeNotSent("Could not send the verification code.")
//  )

  private val verifyCodeResponseMap = Map(
    VerifyCodeV2Request("codesent@sendcode.com", "ABCDEF")          -> VerifyCodeResult.codeVerified(),
    VerifyCodeV2Request("codenotverified@verifycode.com", "FEDCBA") -> VerifyCodeResult.codeNotVerified("Could not verify the verification code.")
  )

  def sendCodeResponseFor(sendCodeV2Request: SendCodeV2Request): SendCodeResult = sendCodeV2Request match {
    case SendCodeV2Request("codesent@sendcode.com")    => SendCodeResult.codeSent()
    case SendCodeV2Request("codenotsent@sendcode.com") => SendCodeResult.codeNotSent("Could not send the verification code.")
    case _                                             => SendCodeResult.codeNotSent("Invalid email")
  }

  def getVerificationCodeFor(email: String): Option[String] =
    verifyCodeResponseMap.find(_._1.email == email).map(_._1.verificationCode).orElse(Some("HIJKLM"))

  def verifyCodeResponseFor(verifyCodeV2Request: VerifyCodeV2Request): VerifyCodeResult = verifyCodeV2Request match {
    case VerifyCodeV2Request("codesent@sendcode.com", "ABCDEF")          => VerifyCodeResult.codeVerified()
    case VerifyCodeV2Request("invalidcodesent@sendcode.com", "123456")   => VerifyCodeResult.codeNotValid(None)
    case VerifyCodeV2Request("codenotsent@sendcode.com", _)              => VerifyCodeResult.codeNotFound("Verification code not found")
    case VerifyCodeV2Request("codenotverified@verifycode.com", "FEDCBA") => VerifyCodeResult.codeNotVerified("Could not verify the verification code.")
    case _                                                               => VerifyCodeResult.codeNotVerified("Invalid email")
  }
}
