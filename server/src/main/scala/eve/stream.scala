package utils

import java.time.Clock
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz._, Scalaz._
import java.util.concurrent.ScheduledExecutorService
import eveapi.data.crest.GetLinkI

import argonaut._, argonaut.Argonaut._, argonaut.Shapeless._
import org.atnos.eff._, org.atnos.eff.syntax.eff._, org.atnos.eff.syntax.all._, org.atnos.eff.all._
import org.http4s._, org.http4s.dsl._, org.http4s.client._, org.http4s.util.CaseInsensitiveString

import stream._

import effects._
import eveapi.utils.TaskEffect._
import shared._

import eveapi._
import eveapi.errors.EveApiError
import eveapi.oauth._
import eveapi.data.crest
import eveapi.data.crest._
import eveapi.utils.Decoders._
import eveapi.compress._
import OAuth2._

object ApiStream {
  implicit def effApplicative[R]: Applicative[Eff[R, ?]] = Eff.EffApplicative

  def members(fleet: Uri): GetLink[Uri, Paginated[crest.Member[Uri]]] =
    GetLinkI[Uri, Paginated[crest.Member[Uri]]](fleet.copy(path = fleet.path + "members/"))
  def wings(fleet: Uri): GetLink[Uri, Paginated[Wing[Uri]]] =
    GetLinkI[Uri, Paginated[crest.Wing[Uri]]](fleet.copy(path = fleet.path + "wings/"))

  val compress = Compress[Uri]()(eveapi.UriPathLens)
  import compress._

  def fleetState(fleetUri: Uri)
    (implicit m: DecodeJson[Paginated[crest.Member[Uri]]], w: DecodeJson[Paginated[Wing[Uri]]]): Free[Lift.Link, FleetState] =
    Lift.get(GetLinkI[Uri, Fleet[Uri]](fleetUri)).flatMap({ f =>
      val m = Lift.get(members(fleetUri))
      val w = Lift.get(wings(fleetUri))
      (m |@| w).tupled.map({ case (m, w) =>
        FleetState(compress.compress(f), m.items.map(x => compress.compress(x)), w.items.map(x => compress.compress(x)))
      })
    })

  def fleetPollSource(fleetUri: Uri, interval: Duration, eval: Lift.Link ~> Api)(implicit ec: ScheduledExecutorService): Process[Api, FleetState] =
    (Process.emit(()) ++ time.awakeEvery(interval)).translate(toApiStream).flatMap[Api, FleetState]({ _ =>
      Process.eval(fleetState(fleetUri).foldMap(eval))
    })

  def toClient(source: Process[Api, FleetState]): Process[Api, ServerToClient] =
    source.stateScan(Scalaz.none[FleetState])(now => State({old =>
        (Some(now), FleetUpdates(now, old.toList.flatMap(o => FleetDiff(o, now))))
      }))

  val fromClient: Sink[Task, ClientToServer] = Process.constant(x => Task.delay(println(x)))

  val toApiStream = new NaturalTransformation[Task, Api] {
    def apply[T](fa: Task[T]): Api[T] = innocentTask(fa)
  }

  def fromApiStream(oauth: OAuth2, token: OAuth2Token) = new NaturalTransformation[Api, Task] {
    def apply[T](fa: Api[T]): Task[T] =
      Eff.detach[Task, EveApiError \/ (T, OAuth2Token)](fa.runReader(oauth).runState(token).runDisjunction).map(_.map(_._1).fold(err => throw err, x => x))
  }
}
