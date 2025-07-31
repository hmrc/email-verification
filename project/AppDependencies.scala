import sbt.*

object AppDependencies {
  private val bootstrapVersion = "9.18.0"
  private val hmrcMongoVersion = "2.7.0"

  private val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion
  )

  private val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30"          % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-test-play-30"         % hmrcMongoVersion,
    "org.scalatestplus"            %% "scalacheck-1-17"                 % "3.2.18.0",
    "org.mockito"                  %% "mockito-scala-scalatest"         % "2.0.0"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
