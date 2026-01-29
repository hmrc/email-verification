import sbt.*

object AppDependencies {

  private val bootstrapVersion        = "10.5.0"
  private val hmrcMongoVersion        = "2.12.0"
  private val scalatestplusVersion    = "3.2.19"

  private val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30"       % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"              % hmrcMongoVersion
  )

  private val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30"          % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-test-play-30"         % hmrcMongoVersion,
    "org.scalatestplus"            %% "scalacheck-1-18"                 % s"$scalatestplusVersion.0",
    "org.scalatestplus"            %% "mockito-5-18"                    % s"$scalatestplusVersion.0"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
