resolvers += Resolver.url("HMRC Sbt Plugin Releases", url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/"

addSbtPlugin("com.typesafe.play"  % "sbt-plugin"          % "2.7.5")

addSbtPlugin("uk.gov.hmrc"        % "sbt-auto-build"      % "2.13.0")

addSbtPlugin("uk.gov.hmrc"        % "sbt-git-versioning"  % "2.2.0")

addSbtPlugin("uk.gov.hmrc"        % "sbt-distributables"  % "2.1.0")

addSbtPlugin("uk.gov.hmrc"        % "sbt-artifactory"     % "1.13.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")