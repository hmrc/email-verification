import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "email-verification"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.PlayImport._
  import play.core.PlayVersion

  private val microserviceBootstrapVersion = "4.4.0"
  private val playAuthVersion = "3.4.0"
  private val playHealthVersion = "1.1.0"
  private val playJsonLoggerVersion = "2.1.1"
  private val playUrlBindersVersion = "1.1.0"
  private val playConfigVersion = "2.1.0"
  private val domainVersion = "3.7.0"
  private val pegdownVersion = "1.6.0"

  private val playReactivemongoVersion = "4.8.0"
  private val hmrcCryptoVersion = "3.1.0"
  private val mongoLibsVersion = "4.8.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "play-authorisation" % playAuthVersion,
    "uk.gov.hmrc" %% "play-health" % playHealthVersion,
    "uk.gov.hmrc" %% "play-url-binders" % playUrlBindersVersion,
    "uk.gov.hmrc" %% "play-config" % playConfigVersion,
    "uk.gov.hmrc" %% "play-json-logger" % playJsonLoggerVersion,
    "uk.gov.hmrc" %% "crypto" % hmrcCryptoVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % mongoLibsVersion,
    "uk.gov.hmrc" %% "play-reactivemongo" % mongoLibsVersion
  )


  object TestDependencies {
    def apply(scope: String) = Seq(
      "uk.gov.hmrc" %% "hmrctest" % "1.9.0" % scope,
      "org.scalatest" %% "scalatest" % "2.2.6" % scope,
      "org.pegdown" % "pegdown" % pegdownVersion % scope,
      "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
      "uk.gov.hmrc" %% "reactivemongo-test" % "1.6.0" % scope,
      "com.github.tomakehurst" % "wiremock" % "1.58" % scope
    )
  }

  def apply() = compile ++ TestDependencies("test") ++ TestDependencies("it")
}

