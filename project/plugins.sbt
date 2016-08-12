addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.9")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")

addSbtPlugin("org.flywaydb" % "flyway-sbt" % "4.0.1")
resolvers += "Flyway" at "https://flywaydb.org/repo"
