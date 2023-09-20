import sbt.*

object AppDependencies {
  private val bootstrapVersion = "7.21.0"
  private val hmrcMongoVersion = "0.74.0"

  private val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % hmrcMongoVersion
  )

  private val test = Seq(
    "uk.gov.hmrc"       %% "government-gateway-test" % "5.2.0"         ,
    "uk.gov.hmrc"       %% "bootstrap-test-play-28"  % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % hmrcMongoVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2",
    "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0",
    "com.vladsch.flexmark"          % "flexmark-all"            % "0.64.6"
  ).map(_ % "test,it")

  def apply(): Seq[ModuleID] = compile ++ test
}
