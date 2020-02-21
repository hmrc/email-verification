import play.sbt.PlayImport._
import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "email-verification"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-25"    % "5.1.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.23.0-play-25"
  )

  val test = Seq(
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "2.0.1"           % "test,it",
    "org.scalatest"           %% "scalatest"                % "3.0.7"           % "test,it",
    "com.github.tomakehurst"  %  "wiremock"                 % "1.58"            % "test,it",
    "org.mockito"             %  "mockito-core"             % "2.27.0"          % "test,it",
    "org.pegdown"             %  "pegdown"                  % "1.6.0"           % "test,it",
    "uk.gov.hmrc"             %% "service-integration-test" % "0.9.0-play-25"   % "test,it",
    "uk.gov.hmrc"             %% "reactivemongo-test"       % "4.16.0-play-25"  % "test,it"
  )

  def apply() = compile ++ test
}
