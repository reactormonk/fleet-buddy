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
    , "org.atnos" %% "eff-scalaz" % "1.7.1"
    , "commons-codec" % "commons-codec" % "1.10"
    , "ch.qos.logback" %  "logback-classic" % "1.1.7"
    , "org.scalacheck" %% "scalacheck" % "1.13.0" % Test
    , "org.reactormonk" %% "counter" % "1.3.3"
    , "org.typelevel" %% "shapeless-scalaz" % "0.4"
  ) ++ Seq(
      "org.http4s" %% "http4s-core"
    , "org.http4s" %% "http4s-dsl"
    , "org.http4s" %% "http4s-blaze-server"
    , "org.http4s" %% "http4s-blaze-client"
    , "org.http4s" %% "http4s-circe"
  ).map(_ % "0.14.0a-SNAPSHOT") ++ Seq(
      "org.tpolecat" %% "doobie-core"
    , "org.tpolecat" %% "doobie-contrib-postgresql"
    , "org.tpolecat" %% "doobie-contrib-specs2"
  ).map(_ % "0.3.0-M1")
).aggregate(clients.map(projectToRef): _*)
  .settings(
      aggregate in flywayMigrate := false
    , aggregate in flywayClean := false
  )
  .dependsOn(sharedJvm)
  .settings(globalSettings: _*)
  .settings(managedResources in Compile ++= Def.task {
    val f1 = (fastOptJS in client in Compile).value.data
    val f1SourceMap = f1.getParentFile / (f1.getName + ".map")
    val f2 = (packageScalaJSLauncher in client in Compile).value.data
    val f3 = (packageJSDependencies in client in Compile).value
    Seq(f1, f1SourceMap, f2, f3)
  }.value)
  .settings(DB.settings: _*)
  .settings(
      flywayUrl in Test := { flywayUrl.value + "test" }
    , flywayDriver in Test := flywayDriver.value
    , flywayUser in Test := flywayUser.value
    , flywayPassword in Test := flywayPassword.value
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](flywayUrl, flywayDriver, flywayUser, flywayPassword),
    buildInfoPackage := "buildInfo"
  )

lazy val client = (project in file("client")).settings(
  scalaVersion := scalaV,
  persistLauncher := true,
  persistLauncher in Test := false,
  libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.8.0"
    , "org.scala-js" %%% "scalajs-java-time" % "0.1.0"
    , "com.lihaoyi" %%% "scalatags" % "0.5.5"
    , "org.reactormonk" %%% "counter" % "1.3.3"
    , "be.doeraene" %%% "scalajs-jquery" % "0.9.0"
    , "me.chrons" %% "diode" % "0.6.0-SNAPSHOT"
    , "org.webjars" % "Semantic-UI" % "2.1.8"
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

initialCommands in server := """
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import doobie.imports._
import org.http4s.Uri._

import eveapi._
import oauth._, OAuth2._
import effects._
import TaskEffect._
import errors._
import models._
import utils._
import EveApi._

val xa = controllers.Loader.xa
import xa.yolo._
def user(name: String): User = User.load(name).transact(xa).unsafePerformSync.get
def transform(u: User): EveApi.Api ~> Task = controllers.Loader.buddy.map(b => ApiStream.fromApiStream(b.oauth, b.client, b.clock, u.token)).unsafePerformSync
def r[T](transform: EveApi.Api ~> Task): EveApi.Api[T] => T = {e => transform(e).unsafePerformSync }
"""
