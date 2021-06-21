import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-27"    % "5.4.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.31.0-play-27"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "government-gateway-test"  % "4.3.0-play-27"   % "test,it",
    "uk.gov.hmrc" %% "reactivemongo-test"       % "4.21.0-play-27"  % "test,it"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
