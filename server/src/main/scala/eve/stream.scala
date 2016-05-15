package eveapi

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
  def fleetState(fleetUri: Uri): Api[FleetState] =
    for {
      fleet <- fetch[Fleet](fleetUri)
      members <- fleet.members.apply()
      wings <- fleet.wings.apply()
    } yield FleetState(fleet, members.items, wings.items)

  def fleetPollSource(fleetUri: Uri, interval: Duration)(implicit ec: ScheduledExecutorService): Process[Api, FleetState] =
    time.awakeEvery(interval).translate(new NaturalTransformation[Task, Api] {
      def apply[A](fa: Task[A]): Api[A] = innocentTask(fa)
    }).flatMap[Api, FleetState]({ _ =>
      Process.eval(fleetState(fleetUri))
    })

}

object WebSocket {
}

case class FleetState(fleet: Fleet, members: List[Member], wings: List[Wing])
