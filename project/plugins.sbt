resolvers += Resolver.url("HMRC Sbt Plugin Releases", url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/"

addSbtPlugin("com.typesafe.play"  % "sbt-plugin"          % "2.6.24")

addSbtPlugin("uk.gov.hmrc"        % "sbt-auto-build"      % "2.6.0")

addSbtPlugin("uk.gov.hmrc"        % "sbt-git-versioning"  % "2.1.0")

addSbtPlugin("uk.gov.hmrc"        % "sbt-distributables"  % "2.0.0")

addSbtPlugin("uk.gov.hmrc"        % "sbt-artifactory"     % "1.2.0")

addSbtPlugin("org.scoverage"      % "sbt-scoverage"       % "1.5.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")