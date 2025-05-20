import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := "2.13.16"

lazy val microservice = Project("email-verification", file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin) *)
  .settings(scalafmtOnCompile := true)
  .settings(ScoverageSettings())
  .settings(scalacOptions ++= Seq(
    "-feature", "-deprecation",
    "-Werror",
    "-Wconf:src=routes/.*&cat=unused-imports:silent",
    "-Wconf:src=routes/.*:s"
  ))
  .settings(
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true
  )
  .settings(PlayKeys.playDefaultPort := 9891)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
