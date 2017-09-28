import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "email-verification"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport._

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "crypto" % "4.4.0",
    "uk.gov.hmrc" %% "logback-json-logger" % "3.1.0",
    "uk.gov.hmrc" %% "microservice-bootstrap" % "5.16.0",
    "uk.gov.hmrc" %% "play-authorisation" % "4.3.0",
    "uk.gov.hmrc" %% "play-config" % "4.3.0",
    "uk.gov.hmrc" %% "play-health" % "2.1.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % "5.2.0",
    "uk.gov.hmrc" %% "play-ui" % "7.7.0"
  )

  val test = Seq(
    "com.github.tomakehurst" % "wiremock" % "1.58" % "test,it",
    "com.typesafe.play" %% "play-test" % PlayVersion.current % "test,it",
    "org.mockito" % "mockito-core" % "2.9.0" % "test,it",
    "org.pegdown" % "pegdown" % "1.6.0" % "test,it",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test,it",
    "uk.gov.hmrc" %% "hmrctest" % "2.4.0" % "test,it",
    "uk.gov.hmrc" %% "reactivemongo-test" % "2.0.0" % "test,it"
  )

  def apply() = compile ++ test
}

