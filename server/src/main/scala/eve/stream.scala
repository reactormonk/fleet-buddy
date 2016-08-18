package utils

import java.time.Clock
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz._, Scalaz._
import java.util.concurrent.ScheduledExecutorService
import eveapi.data.crest.GetLinkI

import argonaut._, argonaut.Argonaut._, argonaut.ArgonautShapeless._
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

  type StreamS = Fx.fx3[Reader[OAuth2, ?], State[OAuth2Token, ?], Task]
  type ApiStream[T] = Eff[StreamS, T]

  def fleetState(fleetUri: Uri)(implicit m: DecodeJson[Paginated[crest.Member[Uri]]], w: DecodeJson[Paginated[Wing[Uri]]]): Free[Lift.Link, Reader[Clock, FleetState]] =
    Lift.get(GetLinkI[Uri, Fleet[Uri]](fleetUri)).flatMap({ f =>
      val m = Lift.get(members(fleetUri))
      val w = Lift.get(wings(fleetUri))
      (m |@| w).tupled.map({ case (m, w) =>
        Reader({clock => FleetState(compress.compress(f), m.items.map(x => compress.compress(x)), w.items.map(x => compress.compress(x)), clock.instant)})
      })
    })

  def fleetPollSource(fleetUri: Uri, interval: Duration, eval: Lift.Link ~> Api)(implicit ec: ScheduledExecutorService): Process[ApiStream, EveApiError \/ FleetState] =
    (Process.emit(()) ++ time.awakeEvery(interval)).translate(toApiStream).flatMap[ApiStream, EveApiError \/ FleetState]({ _ =>
      Process.eval({
        fleetState(fleetUri)
          .foldMap(eval)
          .flatMap({reader => ask[EveApiS, OAuth2].map({(oauth: OAuth2) => reader.run(oauth.clock)})})
          .runDisjunction[EveApiError, StreamS]
      })
    })

  def toClient: Process1[FleetState, ServerToClient] =
    process1.stateScan(Scalaz.none[FleetState])(now => State({old =>
        (Some(now), FleetUpdates(now, old.toList.flatMap(o => FleetDiff(o, now))))
      }))

  val fromClient: Sink[Task, ClientToServer] = Process.constant(x => Task.delay(println(x)))

  val toApiStream = new NaturalTransformation[Task, ApiStream] {
    def apply[T](fa: Task[T]): ApiStream[T] = innocentTask(fa)
  }

  def fromApiStream(oauth: OAuth2, token: OAuth2Token) = new NaturalTransformation[ApiStream, Task] {
    def apply[T](fa: ApiStream[T]): Task[T] =
      Eff.detach[Task, (T, OAuth2Token)](fa.runReader(oauth).runState(token)).map(_._1)
  }

  def toDB[T[_]](source: Process[T, FleetState]): Process[T, Unit] = {
    source.map(models.FleetHistory.insert)
  }
}
