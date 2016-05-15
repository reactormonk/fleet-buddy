package eveapi

import io.circe.Json._
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
  type A = EveApiS

  def bearer(request: Request, token: String) = request.putHeaders("Authorization" -> s"Bearer ${token}")

  def refresh: Api[Unit] =
    for {
      settings <- ask[A, OAuth2Settings]
      token <- StateEffect.get[A, OAuth2Token]
      client <- ask[A, Client]
      clock <- ask[A, Clock]
      newToken <- task[A, OAuth2Token]({
        client.fetchAs[OAuth2Token](
          POST(settings.refreshUri, UrlForm("grant_type" -> "refresh_token", "refresh_token" -> token.refresh_token))
          .putHeaders(encodeAuth(settings))
        )(jsonOf[OAuth2Token]).map(_.copy(generatedAt = Instant.now(clock)))
      })
      _ <- StateEffect.put[A, OAuth2Token](token)
    } yield ()

  def executeOAuth(request: Request): Api[Response] = {
    for {
      settings <- ask[A, OAuth2Settings]
      token <- StateEffect.get[A, OAuth2Token]
      client <- ask[A, Client]
      clock <- ask[A, Clock]
      fetch = client.fetch[Response](bearer(request, token.access_token))(x => Task.now(x))
      _ <- if(token.expired(clock)) refresh else EffMonad[A].point(())
      resp <- task[A, Response](fetch)
      result <- resp.status match {
        case Status.Unauthorized => refresh >> task[A, Response](fetch)
        case _ => task[A, Response](Task.now(resp))
      }
    } yield result
  }

  def fetch[T: Decoder](request: Request): Api[T] = executeOAuth(request).flatMap(resp => task(resp.as[T](jsonOf[T])))
  def fetch[T: Decoder](uri: Uri): Api[T] = fetch[T](Request(method = Method.GET, uri = uri))

  def verify: Api[VerifyAnswer] =
    ask[A, OAuth2Settings].flatMap({ settings =>
      fetch[VerifyAnswer](settings.verifyUri)
    })

}

import EveApi._

case class Link[T](href: Uri) {
  def apply()(implicit ev: Decoder[T]): Api[T] =
    fetch[T](Request(method = Method.GET, uri = href))
}

// Even solar systems (8k in count) aren't paginated. Not implemeting for now.
case class Paginated[T](items: List[T], pageCount: Long, pageCount_str: String, totalCount: Long, totalCount_str: String)

case class Id[T](href: Uri, id: Long, id_str: String, name: String) {
  def link = Link[T](href)
}

/*
 * A Href pointing to a Uri you can POST to, but doesn't allow for GET.
 */
case class Href(href: Uri)

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
  squads: Href,
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
