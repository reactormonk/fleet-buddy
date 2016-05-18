import sbt.Project.projectToRef

lazy val clients = Seq(client)
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

lazy val http4sVersion = "0.14.0a-SNAPSHOT"
lazy val circeVersion = "0.4.1"

scalaVersion := scalaV

lazy val server = (project in file("server")).settings(
  scalaVersion := scalaV,
  pipelineStages := Seq(gzip),
  resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  libraryDependencies ++= Seq(
      "oncue.knobs" %% "core" % "3.6.0a"
    , "org.scalaz" %% "scalaz-core" % "7.2.2"
    , "org.scalaz" %% "scalaz-concurrent" % "7.2.2"
    , "org.atnos" %% "eff-scalaz" % "1.5"
    , "commons-codec" % "commons-codec" % "1.10"
    , "org.tpolecat" %% "doobie-core" % "0.3.0-M1"
    , "ch.qos.logback" %  "logback-classic" % "1.1.7"
    , "org.scalacheck" %% "scalacheck" % "1.13.0" % Test
    , "org.reactormonk" %% "counter" % "1.3.3"
  ) ++ Seq(
      "org.http4s" %% "http4s-core"
    , "org.http4s" %% "http4s-dsl"
    , "org.http4s" %% "http4s-blaze-server"
    , "org.http4s" %% "http4s-blaze-client"
    , "org.http4s" %% "http4s-circe"
  ).map(_ % http4sVersion)
).aggregate(clients.map(projectToRef): _*)
  .dependsOn(sharedJvm)
  .settings(globalSettings: _*)
  .settings(managedResources in Compile ++= {
    val js = (fastOptJS in (client, Compile)).value.data
    Seq(js, file(js + ".map"))
  })

lazy val client = (project in file("client")).settings(
  scalaVersion := scalaV,
  persistLauncher := true,
  persistLauncher in Test := false,
  libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.8.0"
    , "org.scala-js" %%% "scalajs-java-time" % "0.1.0"
    , "com.lihaoyi" %%% "scalatags" % "0.5.5"
    , "org.reactormonk" %%% "counter" % "1.3.3"
  )
).enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJs)
  .settings(globalSettings: _*)

lazy val shared = (crossProject.crossType(CrossType.Pure) in file("shared"))
  .settings(
    scalaVersion := scalaV,
    libraryDependencies ++= Seq(
        "io.circe" %%% "circe-core"
      , "io.circe" %%% "circe-parser"
      , "io.circe" %%% "circe-generic"
      , "io.circe" %%% "circe-java8"
    ).map(_ % circeVersion)
  )
  .settings(globalSettings: _*)


lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

onLoad in Global := (Command.process("project server", _: State)) compose (onLoad in Global).value

scalacOptions ++= Seq(
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
  "-language:existentials"
)
