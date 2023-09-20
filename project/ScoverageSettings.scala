import sbt.*
import scoverage.*

object ScoverageSettings {
  def apply(): Seq[Def.Setting[?]] = {
    Seq(
      ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;.*BuildInfo.*;.*Routes.*;.*RoutesPrefix.*",
      ScoverageKeys.coverageMinimumStmtTotal := 95,
      ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageKeys.coverageHighlighting := true
    )
  }
}
