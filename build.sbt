import sbt.Project.projectToRef

lazy val scalaV = "2.11.8"
lazy val globalSettings = Seq(
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")
  , addCompilerPlugin("com.milessabin" % "si2712fix-plugin_2.11.8" % "1.1.0")
  , addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  , resolvers ++= Seq(
      Resolver.sonatypeRepo("releases")
    , Resolver.sonatypeRepo("snapshots")
    , Resolver.bintrayRepo("oncue", "releases")
  )
)

val eveapiVersion = "0.1-SNAPSHOT"
val doobieVersion = "0.3.0"

scalaVersion := scalaV

lazy val server = (project in file("server")).settings(
  scalaVersion := scalaV,
  pipelineStages := Seq(gzip),
  resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  libraryDependencies ++= Seq(
      "oncue.knobs" %% "core" % "3.6.0a"
    , "org.scalaz" %% "scalaz-core" % "7.2.2"
    , "org.scalaz" %% "scalaz-concurrent" % "7.2.2"
    , "commons-codec" % "commons-codec" % "1.10"
    , "ch.qos.logback" %  "logback-classic" % "1.1.7"
    , "org.scalacheck" %% "scalacheck" % "1.13.0" % Test
    , "org.reactormonk" %% "counter" % "1.3.3"
    , "org.typelevel" %% "shapeless-scalaz" % "0.4"
    , "org.flywaydb" % "flyway-core" % "4.0.1"
  ) ++ Seq(
      "org.http4s" %% "http4s-core"
    , "org.http4s" %% "http4s-dsl"
    , "org.http4s" %% "http4s-blaze-server"
    , "org.http4s" %% "http4s-blaze-client"
  ).map(_ % "0.14.2a") ++ Seq(
      "org.tpolecat" %% "doobie-core"
    , "org.tpolecat" %% "doobie-contrib-postgresql"
    , "org.tpolecat" %% "doobie-contrib-specs2"
  ).map(_ % doobieVersion) ++ Seq(
      "eveapi" %% "blazeargonautapi"
    , "eveapi" %% "compress"
  ).map(_ % eveapiVersion) ++ Seq(
    "io.argonaut" %% "argonaut" % "6.1a",
    "com.github.alexarchambault" %% "argonaut-shapeless_6.1" % "1.1.0-RC2"
  )
)
  .dependsOn(sharedJvm)
  .settings(globalSettings: _*)
  .settings(
    (managedResources in Compile) ++= Seq(
      (compileElm in client).value
    , (compileCss in client).value
    , file("client/semantic/dist/semantic.min.css")
    , file("client/semantic/dist/semantic.min.js")
    , file("client/index.html")
    )
)
  .enablePlugins(JavaServerAppPackaging, DebianPlugin, SystemdPlugin)
  .settings(
      daemonUser in Linux := "fleetbuddy"
    , name in Linux := "fleetbuddy"
    , packageName in Linux := "fleetbuddy"
    , maintainer in Linux := "Simon Hafner <reactormonk@gmail.com>"
    , packageSummary in Linux := "Fleetbuddy. Your friendly fleet helper."
    , executableScriptName := "fleetbuddy"
    , javaOptions in Universal ++= Seq(
        "-J-Xmx950M"
      , "-Dconfigfile=/etc/fleetbuddy/application.conf"
    )
    , debianPackageDependencies in Debian ++= Seq(
        "openjdk-8-jre"
      , "postgresql"
      , "pwgen"
      , "debsums"
    )
    , linuxPackageMappings := {
      linuxPackageMappings.value.map({ linuxPackage =>
        if (linuxPackage.mappings.exists(_._2 == "etc/fleetbuddy/application.conf")) {
          linuxPackage.copy(
            fileData = linuxPackage.fileData.copy(
              user = "fleetbuddy",
              permissions = "600"
            )
          )
        } else { linuxPackage }
      })
    }
)
  .settings(
    test := Def.sequential(
      flywayClean in (flyway, Test),
      flywayMigrate in (flyway, Test),
      test in Test
    ).value
      , flywayClean := flywayClean in flyway
      , flywayMigrate := flywayMigrate in flyway
)
  .enablePlugins(BuildInfoPlugin)
  .settings(
      buildInfoKeys := Seq[BuildInfoKey](flywayUrl, flywayDriver, flywayUser, flywayPassword)
    , buildInfoPackage := "buildInfo"
)
  .settings(ApplicationConf.settings: _*)
  .settings((unmanagedResourceDirectories in Compile) += (resourceDirectory in (flyway, Compile)).value)
  .settings(
    mainClass in Compile := Some("controllers.Loader")
)

lazy val shared = (crossProject.crossType(CrossType.Pure) in file("shared"))
  .settings(
    scalaVersion := scalaV,

    libraryDependencies ++= Seq(
      "eveapi" %% "compress" % eveapiVersion,
      "org.reactormonk" %% "elmtypes" % "0.2"
    )
  )
  .settings(globalSettings: _*)

lazy val flyway = (project in file("."))
  .settings(
    flywayUrl in Test := { flywayUrl.value + "test" }
  , flywayDriver in Test := flywayDriver.value
  , flywayUser in Test := flywayUser.value
  , flywayPassword in Test := flywayPassword.value
  , scalaVersion := scalaV
  , libraryDependencies += "org.tpolecat" %% "doobie-contrib-postgresql" % doobieVersion
)
  .settings(ApplicationConf.settings: _*)


lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

onLoad in Global := (Command.process("project server", _: State)) compose (onLoad in Global).value

lazy val client = project in file("client")

val compileElm = taskKey[File]("Compile the elm into a elm.css")

(compileElm in client) := {
  val codec = (baseDirectory in client).value / "Codec.elm"
  (runner in (sharedJvm, run)).value.run("ElmTypes", Attributed.data((fullClasspath in sharedJvm in Compile).value), Seq(codec.toString), streams.value.log)
  if (Process("elm-make Main.elm --yes --output=elm.js", file("client")).! != 0) {throw new Exception("elm build failed!")}
  (baseDirectory in client).value / "elm.js"
}

val compileCss = taskKey[File]("Compile the elm into an styles.css")

(compileCss in client) := {
  if (Process("node_modules/.bin/elm-css Stylesheets.elm", file("client")).! != 0) {throw new Exception("elm build failed!")}
  (baseDirectory in client).value / "styles.css"
}

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-inaccessible",
  "-Ywarn-nullary-override",
  "-Ywarn-numeric-widen",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-encoding", "utf8"
)
