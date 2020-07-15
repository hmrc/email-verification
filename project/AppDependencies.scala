import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-26"    % "1.13.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.30.0-play-26"
  )

  val test = Seq(
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "3.1.1"           % "test,it",
    "org.scalatest"           %% "scalatest"                % "3.0.7"           % "test,it",
    "com.github.tomakehurst"  %  "wiremock"                 % "1.58"            % "test,it",
    "org.mockito"             %  "mockito-core"             % "2.27.0"          % "test,it",
    "uk.gov.hmrc"             %% "government-gateway-test"    % "3.1.0"   % "test,it",
    "uk.gov.hmrc"             %% "reactivemongo-test"       % "4.21.0-play-26"  % "test,it"
  )

  val overrides = {
    val jettyFromWiremockVersion = "9.4.15.v20190215"
    Seq(
      "org.eclipse.jetty"           % "jetty-client"       % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty"           % "jetty-continuation" % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty"           % "jetty-http"         % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty"           % "jetty-io"           % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty"           % "jetty-security"     % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty"           % "jetty-server"       % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty"           % "jetty-servlet"      % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty"           % "jetty-servlets"     % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty"           % "jetty-util"         % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty"           % "jetty-webapp"       % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty"           % "jetty-xml"          % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty.websocket" % "websocket-api"      % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty.websocket" % "websocket-client"   % jettyFromWiremockVersion % "it",
      "org.eclipse.jetty.websocket" % "websocket-common"   % jettyFromWiremockVersion % "it",
      "com.typesafe.akka"          %% "akka-stream"        % "2.5.23"     force(),
      "com.typesafe.akka"          %% "akka-protobuf"      % "2.5.23"     force(),
      "com.typesafe.akka"          %% "akka-slf4j"         % "2.5.23"     force(),
      "com.typesafe.akka"          %% "akka-actor"         % "2.5.23"     force(),
      "com.typesafe.akka"          %% "akka-http-core"     % "10.0.15"    force()
    )
  }

  def apply(): Seq[ModuleID] = compile ++ test
}
