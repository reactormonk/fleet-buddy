package eveapi

import java.time.Clock
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz._, Scalaz._
import java.util.concurrent.ScheduledExecutorService

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._, io.circe.java8.time._
import org.atnos.eff._, org.atnos.eff.syntax.eff._, org.atnos.eff.syntax.all._, org.atnos.eff.all._
import org.http4s._, org.http4s.dsl._, org.http4s.client._, org.http4s.circe._

import stream._

import effects._
import TaskEffect._
import oauth._
import OAuth2._
import errors._
import utils.Decoders._

import EveApi._

object ApiStream {
  implicit def effApplicative[R]: Applicative[Eff[R, ?]] = Eff.EffApplicative

  def fleetState(fleetUri: Uri): Api[FleetState] =
    fetch[Fleet](fleetUri).flatMap({ f =>
      (f.members.apply() |@| f.wings.apply()).tupled.map({ case (m, w) =>
        FleetState(f, m.items, w.items)
      })
    })

  def fleetPollSource(fleetUri: Uri, interval: Duration)(implicit ec: ScheduledExecutorService): Process[Api, FleetState] =
    time.awakeEvery(interval).translate(toApiStream).flatMap[Api, FleetState]({ _ =>
      Process.eval(fleetState(fleetUri))
    })

  def toClient(fleetUri: Uri, pollInterval: Duration)(implicit ec: ScheduledExecutorService): Process[Api, ServerToClient] =
    fleetPollSource(fleetUri, pollInterval)

  val fromClient: Sink[Task, ClientToServer] = Process.constant(x => Task.delay(println(x)))

  val toApiStream = new NaturalTransformation[Task, Api] {
    def apply[A](fa: Task[A]): Api[A] = innocentTask(fa)
  }

  def fromApiStream(settings: OAuth2Settings, client: Client, clock: Clock, state: OAuth2Token) = new NaturalTransformation[Api, Task] {
    def apply[A](fa: Api[A]): Task[A] = Eff.detach[Task, Err \/ (A, OAuth2Token)](fa.runReader(settings).runReader(client).runReader(clock).runState(state).runDisjunction).map(_.map(_._1).fold(err => throw err, x => x))
  }
}
