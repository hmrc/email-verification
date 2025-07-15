import sbt.*

object AppDependencies {
  private val bootstrapVersion = "9.14.0"
  private val hmrcMongoVersion = "2.6.0"

  private val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion
  )

  private val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "government-gateway-test-play-30" % "7.1.0",
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30"          % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-test-play-30"         % hmrcMongoVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"            % "2.19.1",
    "org.scalatestplus"            %% "scalacheck-1-17"                 % "3.2.18.0",
    "com.vladsch.flexmark"          % "flexmark-all"                    % "0.64.8",
    "org.mockito"                  %% "mockito-scala-scalatest"         % "2.0.0"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
