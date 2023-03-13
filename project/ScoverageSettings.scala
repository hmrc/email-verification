import scoverage._
import sbt._

object ScoverageSettings {
  def apply(): Seq[Def.Setting[_]] = {
    Seq(
      ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;.*BuildInfo.*;.*Routes.*;.*RoutesPrefix.*",
      ScoverageKeys.coverageMinimumStmtTotal := 95,
      ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageKeys.coverageHighlighting := true
    )
  }
}
