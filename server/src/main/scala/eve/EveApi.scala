package eveapi

import java.time._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz._, Scalaz._
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._, io.circe.java8.time._
import org.atnos.eff._, org.atnos.eff.syntax.eff._, org.atnos.eff.syntax.all._, org.atnos.eff.all._
import org.http4s._, org.http4s.dsl._, org.http4s.client._, org.http4s.circe._

import effects._
import TaskEffect._
import oauth._
import OAuth2._
import errors._

object EveApi {
  type EveApiS = Reader[OAuth2Settings, ?] |: Reader[Client, ?] |: Reader[Clock, ?] |: (Err \/ ?) |: Task |: State[OAuth2Token, ?] |: NoEffect
  type Api[T] = Eff[EveApiS, T]
  type R = EveApiS

  def bearer(request: Request, token: String) = request.putHeaders("Authorization" -> s"Bearer ${token}")

  def refresh()(
    implicit a: Reader[OAuth2Settings, ?] <= R, f: Task <= R, p: State[OAuth2Token, ?] <= R, client: Reader[Client, ?] <= R, err: Err \/ ? <= R, clock: Reader[Clock, ?] <= R
  ): Eff[R, Unit] =
    for {
      settings <- ask[R, OAuth2Settings]
      token <- StateEffect.get[R, OAuth2Token]
      client <- ask[R, Client]
      clock <- ask[R, Clock]
      newToken <- task[R, OAuth2Token]({
        client.fetchAs[OAuth2Token](
          POST(settings.refreshUri, UrlForm("grant_type" -> "refresh_token", "refresh_token" -> token.refresh_token))
          .putHeaders(encodeAuth(settings))
        )(jsonOf[OAuth2Token]).map(_.copy(generatedAt = Instant.now(clock)))
      })
      _ <- StateEffect.put[R, OAuth2Token](token)
    } yield ()

  def executeOAuth(request: Request)(
    implicit a: Reader[OAuth2Settings, ?] <= R, f: Task <= R, p: State[OAuth2Token, ?] <= R, c: Reader[Client, ?] <= R, err: Err \/ ? <= R, cl: Reader[Clock, ?] <= R
  ): Eff[R, Response] = {
    for {
      settings <- ask[R, OAuth2Settings]
      token <- StateEffect.get[R, OAuth2Token]
      client <- ask[R, Client]
      clock <- ask[R, Clock]
      fetch = client.fetch[Response](bearer(request, token.access_token))(x => Task.now(x))
      _ <- if(token.expired(clock)) refresh()(a, f, p, c, err, cl) else EffMonad[R].point(())
      resp <- task[R, Response](fetch)
      result <- resp.status match {
        case Status.Unauthorized => refresh()(a, f, p, c, err, cl) >> task[R, Response](fetch)
        case _ => task[R, Response](Task.now(resp))
      }
    } yield result
  }

  def fetchApi[T](request: Request)(implicit ev: Decoder[T]): Api[T] = executeOAuth(request).flatMap(resp => task(resp.as[T](jsonOf[T])))

  def verify: Api[VerifyAnswer] =
    ask[R, OAuth2Settings].flatMap({ settings =>
      fetchApi[VerifyAnswer](Request(method = Method.GET, uri = settings.verifyUri))
    })
}

import EveApi._

case class Link[T](href: Uri) {
  def apply()(implicit ev: Decoder[T]): Api[T] =
    fetchApi[T](Request(method = Method.GET, uri = href))
}

trait Paginated[T] {
  def toList: Api[List[T]]
}

trait Id[T] {
  def href: String
  def link: Link[T] = ???
  def id: Long
  def id_str: String
  def name: String
}

case class Fleet(
  isFreeMove: Boolean,
  isRegistered: Boolean,
  isVoiceEnabled: Boolean,
  members: Link[Paginated[Member]],
  motd: String,
  wings: Link[Paginated[Wing]]
)

case class Member(
  boosterID: Int,
  boosterID_str: String,
  boosterName: String,
  character: Character,
  href: Link[Member],
  joinTime: Instant,
  roleID: Int,
  roleID_str: String,
  roleName: String,
  ship: Id[Ship],
  solarSystem: Id[SolarSystem],
  squadID: Long,
  squadID_str: String,
  station: Id[Station],
  takesFleetWarp: Boolean,
  wingID: Long,
  wingID_str: String)

case class Wing(
  id: Long,
  id_str: String,
  name: String,
  squads: Unit,
  squadsList: List[Squad]
)

case class Squad(
  id: Long,
  id_str: String,
  name: String
)

case class Character(
  // capsuleer: Link[Capsuleer], // Not enabled yet
)

case class SolarSystem()

case class Station()

case class Ship()

case class Capsuleer()

case class VerifyAnswer(
  CharacterID: Long,
  CharacterName: String,
  Scopes: String,
  TokenType: String,
  CharacterOwnerHash: String
)
