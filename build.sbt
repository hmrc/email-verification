import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, integrationTestSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

lazy val microservice = Project("email-verification", file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory): _*)
  .settings(majorVersion := 1)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(ScoverageSettings())
  .settings(SilencerSettings())
  .settings(ScalariformSettings())
  .settings(scalaVersion := "2.12.12")
  .settings(scalacOptions ++= Seq("-Xfatal-warnings", "-feature", "-deprecation"))
  .settings(
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(integrationTestSettings())
  .settings(resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/")
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(PlayKeys.playDefaultPort := 9891)