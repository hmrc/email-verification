import sbt.*
import scoverage.*

object ScoverageSettings {

  val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "Reverse.*",
    ".*BuildInfo.*",
    ".*Routes.*",
    ".*RoutesPrefix.*"
  )

  def apply(): Seq[Setting[?]] = Seq(
    ScoverageKeys.coverageMinimumStmtTotal := 94,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";")
  )

}
