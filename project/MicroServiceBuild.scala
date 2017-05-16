import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "email-verification"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport._

  private val microserviceBootstrapVersion = "5.15.0"
  private val playAuthVersion = "4.3.0"
  private val playHealthVersion = "2.1.0"
  private val logbackJsonLoggerVersion = "3.1.0"
  private val playUiVersion = "7.2.1"
  private val playConfigVersion = "4.3.0"
  private val domainVersion = "4.1.0"
  private val pegdownVersion = "1.6.0"

  private val playReactivemongoVersion = "5.2.0"
  private val hmrcCryptoVersion = "4.4.0"
  private val mockitoVersion = "2.6.2"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "play-authorisation" % playAuthVersion,
    "uk.gov.hmrc" %% "play-health" % playHealthVersion,
    "uk.gov.hmrc" %% "play-ui" % playUiVersion,
    "uk.gov.hmrc" %% "play-config" % playConfigVersion,
    "uk.gov.hmrc" %% "logback-json-logger" % logbackJsonLoggerVersion,
    "uk.gov.hmrc" %% "crypto" % hmrcCryptoVersion,
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion
  )

  val test = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % "test,it",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test,it",
    "org.pegdown" % "pegdown" % pegdownVersion % "test,it",
    "com.typesafe.play" %% "play-test" % PlayVersion.current % "test,it",
    "uk.gov.hmrc" %% "reactivemongo-test" % "2.0.0" % "test,it",
    "com.github.tomakehurst" % "wiremock" % "1.58" % "test,it",
    "org.mockito" % "mockito-core" % mockitoVersion % "test,it"
  )

  def apply() = compile ++ test
}

