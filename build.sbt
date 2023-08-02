import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, integrationTestSettings, scalaSettings}

lazy val microservice = Project("email-verification", file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin) *)
  .settings(majorVersion := 1)
  .settings(scalaSettings *)
  .settings(defaultSettings() *)
  .settings(ScoverageSettings())
  .settings(ScalariformSettings())
  .settings(scalaVersion := "2.13.8")
  .settings(scalacOptions ++= Seq(
    "-feature", "-deprecation",
    "-Werror",
    "-Wconf:src=routes/.*&cat=unused-imports:silent"
  ))
  .settings(
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings) *)
  .settings(integrationTestSettings())
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(PlayKeys.playDefaultPort := 9891)