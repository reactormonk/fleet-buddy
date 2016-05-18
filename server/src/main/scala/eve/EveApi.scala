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

  def executeOAuth[T](request: Request)(decoder: Response => Task[T]): Api[T] = {
    for {
      settings <- ask[A, OAuth2Settings]
      token <- StateEffect.get[A, OAuth2Token]
      client <- ask[A, Client]
      clock <- ask[A, Clock]
      _ <- if(token.expired(clock)) refresh else EffMonad[A].point(())
      fetch = client.fetch[(Status, String) \/ T](bearer(request, token.access_token))({ response =>
        response.status match {
          case Status.Ok => decoder(response).map(\/-.apply)
          case _ => response.as[String].map(body => -\/((response.status, body)))
        }
      })
      maybeResponse <- task[A, (Status, String) \/ T](fetch)
      result <- maybeResponse match {
        case -\/((Status.Unauthorized, _)) => refresh >> task[A, (Status, String) \/ T](fetch).map(_.leftMap[Err]((EveApiStatusFailed.apply _).tupled)).flatMap(fromDisjunction[A, Err, T])
        case \/-(resp) => task[A, T](Task.now(resp))
        case -\/((status, body)) => fromDisjunction[A, Err, T](-\/(EveApiStatusFailed(status, body)))
      }
    } yield result
  }

  def fetch[T: Decoder](request: Request): Api[T] = executeOAuth(request)(_.as[String].map({str => decode[T](str).fold(err => throw JsonParseError(err), x => x) }))
  def fetch[T: Decoder](uri: Uri): Api[T] = fetch[T](Request(method = Method.GET, uri = uri))

  def verify: Api[VerifyAnswer] =
    ask[A, OAuth2Settings].flatMap({ settings =>
      fetch[VerifyAnswer](settings.verifyUri)
    })

  implicit def fetcher[T] = new Fetcher[T] {
    type Monad[A] = Api[A]
    override def apply(link: Link[T])(implicit ev: Decoder[T]): Api[T] = {
      val parsed: Err \/ Uri = Uri.fromString(link.href).leftMap(errors.ParseFailure.apply)
      for {
        uri <- fromDisjunction[EveApiS, Err, Uri](parsed)
        result <- fetch[T](uri)
      } yield result
    }
  }
}

case class VerifyAnswer(
  CharacterID: Long,
  CharacterName: String,
  Scopes: String,
  TokenType: String,
  CharacterOwnerHash: String
)
