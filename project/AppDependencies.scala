import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-28"    % "5.14.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "8.0.0-play-28"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "government-gateway-test"  % "4.6.0-play-28"   % "test,it",
    "uk.gov.hmrc" %% "reactivemongo-test"       % "5.0.0-play-28"   % "test,it",
    "org.mockito" %% "mockito-scala-scalatest"  % "1.16.37"         % "test,it",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.2" % "test,it"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
