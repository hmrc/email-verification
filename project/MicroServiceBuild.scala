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
    "uk.gov.hmrc" %% "microservice-bootstrap" % "6.18.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.2.0"
  )

  val test = Seq(
    "com.github.tomakehurst" % "wiremock" % "1.58" % "test,it",
    "org.mockito" % "mockito-core" % "2.9.0" % "test,it",
    "org.pegdown" % "pegdown" % "1.6.0" % "test,it",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test,it",
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % "test,it",
    "uk.gov.hmrc" %% "reactivemongo-test" % "3.1.0" % "test,it"
  )

  def apply() = compile ++ test
}

