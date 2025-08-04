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

package uk.gov.hmrc.emailverification.services

import config.AppConfig
import uk.gov.hmrc.emailverification.models.VerifiedEmail
import uk.gov.hmrc.emailverification.repositories.{RepositoryBaseSpec, VerifiedHashedEmailMongoRepository}
import uk.gov.hmrc.http.HeaderCarrier

class VerifiedEmailServiceSpec extends RepositoryBaseSpec {

  val lowerCaseEmail = "user@email.com"
  val mixedCaseEmail = "uSeR@eMaiL.com"
  val anotherEmail = "another.user@email.com"
  def emailWithNumber(num: Int) = s"user$num@email.com"
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConfig: AppConfig = mock[AppConfig]
  when(mockConfig.verifiedEmailRepoHashKey).thenReturn("somehashkey")

  lazy val hashedEmailRepo = new VerifiedHashedEmailMongoRepository(mongoComponent, mockConfig)
  lazy val service = new VerifiedEmailService(hashedEmailRepo)

  "insert" should {
    "insert a document into hashed emails repo" in {
      await(hashedEmailRepo.find(lowerCaseEmail)) shouldBe None

      await(service.insert(lowerCaseEmail))

      val hashedEmailRepoDoc = await(hashedEmailRepo.find(lowerCaseEmail))
      hashedEmailRepoDoc shouldBe Some(VerifiedEmail(lowerCaseEmail))
    }

    "not blow up if a different email exists" in {
      await(hashedEmailRepo.ensureIndexes())

      await(hashedEmailRepo.find(lowerCaseEmail)) shouldBe None
      await(hashedEmailRepo.find(anotherEmail))   shouldBe None

      await(service.insert(lowerCaseEmail))
      await(service.insert(anotherEmail))

      await(hashedEmailRepo.find(lowerCaseEmail)) shouldBe Some(VerifiedEmail(lowerCaseEmail))
      await(hashedEmailRepo.find(anotherEmail))   shouldBe Some(VerifiedEmail(anotherEmail))
    }
  }

  "find" should {
    "return verified email if it exists" in {
      service.find(lowerCaseEmail).futureValue shouldBe None
      await(hashedEmailRepo.insert(lowerCaseEmail))

      await(service.find(lowerCaseEmail)) shouldBe Some(VerifiedEmail(lowerCaseEmail))
    }

    "return lowercase verified email if it exists when checking for mixed case email" in {
      service.find(mixedCaseEmail).futureValue shouldBe None
      await(hashedEmailRepo.insert(lowerCaseEmail))

      await(service.find(mixedCaseEmail)) shouldBe Some(VerifiedEmail(lowerCaseEmail))
    }

    "return None if email does not exist" in {
      await(service.find(lowerCaseEmail)) shouldBe None
    }
  }

  "isVerified" should {
    "return false if email does not exist when checking the hashed email collection" in {
      await(service.isVerified(lowerCaseEmail)) shouldBe false
    }

    "return true if email exists when checking the hashed email collection" in {
      await(hashedEmailRepo.find(lowerCaseEmail)) shouldBe None
      await(service.isVerified(lowerCaseEmail))   shouldBe false
      await(hashedEmailRepo.insert(lowerCaseEmail))
      await(service.isVerified(lowerCaseEmail)) shouldBe true
    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(hashedEmailRepo.ensureIndexes())
  }
}
