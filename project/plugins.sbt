resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.typesafe.play"  % "sbt-plugin"          % "2.8.18")

addSbtPlugin("uk.gov.hmrc"        % "sbt-auto-build"      % "3.8.0")

addSbtPlugin("uk.gov.hmrc"        % "sbt-distributables"  % "2.1.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.0")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
