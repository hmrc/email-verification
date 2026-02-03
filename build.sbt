import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 2
ThisBuild / scalaVersion := "3.3.7"

lazy val microservice = Project("email-verification", file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin) *)
  .settings(scalafmtOnCompile := true)
  .settings(ScoverageSettings())
  .settings(scalacOptions ++= Seq(
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
