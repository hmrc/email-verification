import play.routes.compiler.StaticRoutesGenerator
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings._
  import uk.gov.hmrc.SbtAutoBuildPlugin
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
  import uk.gov.hmrc.versioning.SbtGitVersioning

  val appName: String

  lazy val appDependencies : Seq[ModuleID] = ???
  lazy val plugins : Seq[Plugins] = Seq.empty
  lazy val playSettings : Seq[Setting[_]] = Seq.empty
  lazy val silencerDependency: Seq[ModuleID] = Seq(
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.4.3" cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % "1.4.3" % Provided cross CrossVersion.full
  )

  private lazy val scoverageSettings = {

    import scoverage._

    Seq(
      ScoverageKeys.coverageExcludedPackages :=
        """<empty>;
          |Reverse.*;
          |.*BuildInfo.*;
          |.*Routes.*;
          |.*RoutesPrefix.*;""".stripMargin,
      ScoverageKeys.coverageMinimum := 80,
      ScoverageKeys.coverageFailOnMinimum := false,
      ScoverageKeys.coverageHighlighting := true
    )
  }

  lazy val microservice = Project(appName, file("."))

    .enablePlugins(Seq(play.sbt.PlayScala,SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins : _*)
    .settings(majorVersion := 0)
    .settings(playSettings ++ scoverageSettings: _*)
    .settings(scalaSettings: _*)
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(scalaVersion := "2.11.12")
    .settings(
      libraryDependencies ++= appDependencies ++ silencerDependency,
      retrieveManaged := true,
      evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
        scalacOptions ++= Seq(
        "-encoding", "UTF-8",   // source files are in UTF-8
        "-deprecation",         // warn about use of deprecated APIs
        "-unchecked",           // warn about unchecked type parameters
        "-feature",             // warn about misused language features
        "-Xfatal-warnings",     // fatal warnings become compile error
        "-language:postfixOps", // allow postfix operator notation
        "-Yno-adapted-args",    // warns about confusing tuples or arg lists
        "-Ywarn-value-discard", // warns about unused non-Unit returned expressions
        "-Ywarn-dead-code",     // warns about unreachable code
        "-Xlint"                // enable handy linter warnings
      )
    )
    .configs(IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
    .settings(
      Keys.fork in IntegrationTest := false,
      unmanagedSourceDirectories in IntegrationTest <<= (baseDirectory in IntegrationTest)(base => Seq(base / "it")),
      addTestReportOption(IntegrationTest, "int-test-reports"),
      testGrouping in IntegrationTest := TestPhases.oneForkedJvmPerTest((definedTests in IntegrationTest).value),
      parallelExecution in IntegrationTest := false)
    .settings(
      resolvers += Resolver.bintrayRepo("hmrc", "releases"),
      resolvers += Resolver.jcenterRepo
    )
}

private object TestPhases {

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
    tests map {
      test => new Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
    }
}
