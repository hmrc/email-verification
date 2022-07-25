import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28"  % "6.3.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % "0.68.0"
  )

  val test = Seq(
    "uk.gov.hmrc"       %% "government-gateway-test"  % "4.9.0"   % "test,it",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28"  % "0.68.0"  % "test,it",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.2" % "test,it"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
