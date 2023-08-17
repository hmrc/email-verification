import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "7.21.0"

  private val compile = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "0.74.0"
  )

  private val test = Seq(
    "uk.gov.hmrc"       %% "government-gateway-test" % "5.1.0"          % "test,it",
    "uk.gov.hmrc"       %% "bootstrap-test-play-28"  % bootstrapVersion % "test,it",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.74.0"         % "test,it",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.3" % "test,it"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
