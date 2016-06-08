package oauth

import org.atnos.eff._, org.atnos.eff.syntax.all._, org.atnos.eff.all._
import scalaz._, Scalaz._
import scalaz.concurrent._
import scala.util.Random
import scala.io.Codec
import org.apache.commons.codec.binary.Base64
import org.http4s._, org.http4s.dsl._, org.http4s.client._, org.http4s.circe._, org.http4s.util.{CaseInsensitiveString => CIS}
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import java.time._
import java.time.temporal.ChronoUnit._

import effects._
import TaskEffect._
import errors._
import utils._

case class OAuth2ClientSettings(
  loginPath: String
)

case class OAuth2Settings(
  authorizationUri: Uri,
  accessTokenUri: Uri,
  callbackUri: Uri,
  verifyUri: Uri,
  clientID: String,
  clientSecret: String,
  refreshUri: Uri,
  scope: Option[String]
)

case class OAuth2Token(
  access_token: String,
  token_type: String,
  expires_in: Int,
  refresh_token: String,
  generatedAt: Instant = Instant.now()
) {
  def expired(at: Clock) = Instant.now(at).isAfter(generatedAt.plus((expires_in * 0.9).toLong, SECONDS))
}

// TODO convert to a State
case class OAuth2State(seed: Long) {
  val tokens = scala.collection.concurrent.TrieMap[String, Unit]()
  val rng = new Random(seed)
  def token = {
    val t = rng.alphanumeric.take(40).mkString("")
    tokens += (t -> ())
    t
  }
  def popToken(token: String): Err \/ Unit = {
    if (tokens.contains(token)) {
      tokens -= token
      \/-(())
    } else {
      -\/(StateNotFound(token, tokens.keySet.toSet))
    }
  }
}

case class ExchangeToken(code: String, state: String)

object OAuth2 {
  type ServicePart = PartialFunction[Request, Task[Response]]
  implicit def tuple2header(t: (String, String)) = Header(t._1, t._2)

  implicit def oauth2Decoder = Decoder.instance({ c =>
    for {
      ac <- (c downField "access_token").as[String]
      tt <- (c downField "token_type").as[String]
      ei <- (c downField "expires_in").as[Int]
      rt <- (c downField "refresh_token").as[String]
    } yield OAuth2Token(ac, tt, ei, rt)
  })

  def redirectoToProvider[R]()(
    implicit r: Reader[OAuth2Settings, ?] <= R, s: Reader[OAuth2State, ?] <= R, t: Task <= R
  ): Eff[R, Response] =
    for {
      settings <- ask[R, OAuth2Settings]
      state <- ask[R, OAuth2State]
      result <- innocentTask({
        Found(settings.authorizationUri
          .withQueryParam("response_type", "code")
          .withQueryParam("redirect_uri", settings.callbackUri.toString)
          .withQueryParam("client_id", settings.clientID)
          .withQueryParam("scope", settings.scope.getOrElse(""))
          .withQueryParam("state", state.token)
        )
      })
    } yield result

  def encodeAuth(settings: OAuth2Settings): Header = {
    val encodedAuth = Base64.encodeBase64String(Codec.toUTF8(s"${settings.clientID}:${settings.clientSecret}")).toString
    Header("Authorization", s"Basic ${encodedAuth}")
  }

  def exchangeOAuthToken[R](exchange: ExchangeToken)(
    implicit c: Reader[Client, ?] <= R, r: Reader[OAuth2Settings, ?] <= R, s: Reader[OAuth2State, ?] <= R, t: Task <= R, d: Err \/ ? <= R, clock: Reader[Clock, ?] <= R
  ): Eff[R, OAuth2Token] = for {
    client <- ask[R, Client]
    settings <- ask[R, OAuth2Settings]
    state <- ask[R, OAuth2State]
    clock <- ask[R, Clock]
    response <- innocentTask[R, Err \/ OAuth2Token]({
      state.popToken(exchange.state).fold(
        x => Task.now(x.left[OAuth2Token]), _ => {
          client.fetchAs[OAuth2Token]( // TODO better error handling
            POST.apply(settings.accessTokenUri, UrlForm("grant_type" -> "authorization_code", "code" -> exchange.code))
              .putHeaders(encodeAuth(settings))
          )(jsonOf[OAuth2Token]).map(_.copy(generatedAt = Instant.now(clock)))
        }.attempt.map(_.leftMap(ThrownException.apply)))
    })
    result <- fromDisjunction(response)
  } yield result

  def oauthService[R](storeToken: OAuth2Token => Task[Response])(
    implicit c: Reader[Client, ?] <= R, r: Reader[OAuth2Settings, ?] <= R, s: Reader[OAuth2State, ?] <= R, t: Task <= R, d: Err \/ ? <= R, clock: Reader[Clock, ?] <= R, sett: Reader[OAuth2ClientSettings, ?] <= R
  ): Eff[R, ServicePart] = {
    for {
      cli <- ask[R, OAuth2ClientSettings]
      settings <- ask[R, OAuth2Settings]
      state <- ask[R, OAuth2State]
      clock <- ask[R, Clock]
      client <- ask[R, Client]
    } yield {
      case r @ GET -> Root / cli.loginPath => EffInterpretation.detach[Task, Response](
        redirectoToProvider[Reader[OAuth2Settings, ?] |: Reader[OAuth2State, ?] |: Task |: NoEffect]().runReader(settings).runReader(state)
      )
      // case r @ GET -> Root / settings.callbackUri.path
      case r @ GET -> Root / "callback" => {
        val token = for {
          code <- r.params.get("code")
          state <- r.params.get("state")
        } yield ExchangeToken(code, state)
        Task.now{token.getOrElse{throw new ParseError(s"Couldn't find all parameters in the callback. Params: ${r.params}")}}.flatMap({ token =>
          exchangeOAuthToken[Reader[OAuth2Settings, ?] |: Reader[OAuth2State, ?] |: Reader[Client, ?] |: Reader[Clock, ?] |: (Err \/ ?) |: Task |: NoEffect](token)
            .runReader(settings).runReader(client).runReader(clock).runReader(state)
            .runDisjunction.detach.map(_.fold[OAuth2Token](err => throw err, x => x)).flatMap(storeToken)
        })
      }
    }
  }
}

case class OAuthAuth(key: PrivateKey, clock: Clock) {
  def setCookie[R](message: String): Cookie = {
    val signed = Crypto.signToken[Reader[PrivateKey, ?] |: Reader[Clock, ?] |: NoEffect](message).runReader(key).runReader(clock).run
    Cookie("authcookie", signed)
  }

  def verifyCookie[R](cookies: headers.Cookie)(implicit key: Reader[PrivateKey, ?] <= R, clock: Reader[Clock, ?] <= R): Eff[R, Option[String]] = {
    cookies.values.list.find(_.name == "authcookie")
      .map({c => Crypto.validateSignedToken[R](c.content) })
      .sequence.map(_.flatten)
  }

  def maybeAuth(request: Request): Option[String] = {
    headers.Cookie.from(request.headers).flatMap(c =>
      verifyCookie[Reader[PrivateKey, ?] |: Reader[Clock, ?] |: NoEffect](c).runReader(key).runReader(clock).run
    )
  }
}
