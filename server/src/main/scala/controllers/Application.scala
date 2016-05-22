package controllers

import org.atnos.eff._, org.atnos.eff.syntax.all._, org.atnos.eff.all._
import java.time.Clock
import knobs.{ CfgText, Configured }
import knobs.{Required, ClassPathResource, Config}
import scala.concurrent.duration.Duration
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import org.http4s._, org.http4s.dsl._, org.http4s.server._
import org.http4s.Uri.{ Authority, RegName }
import org.http4s.client.Client
import org.http4s.server.blaze._
import doobie.imports._

import eveapi._
import oauth._, OAuth2._
import effects._
import TaskEffect._
import errors._
import models._
import utils._

case class FleetBuddy(oauth: OAuth2Settings, host: String, port: Int, appKey: PrivateKey, pollInterval: Duration, xa: Transactor[Task]) {
  val seed = new java.security.SecureRandom().nextLong
  val clock = Clock.systemUTC()
  val oauthState = OAuth2State(seed)
  val oauthClientSettings = OAuth2ClientSettings("login")
  val client = org.http4s.client.blaze.PooledHttp1Client()
  implicit val scheduler = scalaz.stream.DefaultScheduler

  val db = scala.collection.concurrent.TrieMap[Long, User]()

  def getUser(id: String): Task[Option[User]] = User.load(id.toLong).transact(xa)
  def addUser(user: User): Task[Unit] = User.upsert(user).transact(xa)

  val storeToken: OAuth2Token => Task[Response] = { token =>
    val verified: Task[Err \/ (VerifyAnswer, OAuth2Token)] =
      EveApi.verify.runReader(clock).runReader(oauth).runReader(client).runState(token).runDisjunction.detach
    verified.flatMap({ _ match {
      case -\/(err) => {
        err.printStackTrace
        InternalServerError(err.toString)
      }
      case \/-((answer, token)) => {
        val user = User(answer.CharacterID, answer.CharacterName, token)
        addUser(user).flatMap({ _ =>
          Found(Uri(path="/")).map(_.addCookie(oauthauth.setCookie(answer.CharacterID.toString)))
        })
      }
    }})
  }

  val oauthserviceTask: Eff[(Err \/ ?) |: Task |: NoEffect, ServicePart] =
    OAuth2.oauthService[Reader[Client, ?] |: Reader[OAuth2Settings, ?] |: Reader[OAuth2State, ?] |: Reader[Clock, ?] |: Reader[OAuth2ClientSettings, ?] |: (Err \/ ?) |: Task |: NoEffect](storeToken)
      .runReader(clock).runReader(oauth).runReader(oauthClientSettings).runReader(oauthState).runReader(client)
  val oauthservice = oauthserviceTask.runDisjunction.detach.run.fold(err => throw new IllegalArgumentException(s"Loading failed: $err"), x => x)
  val oauthauth = OAuthAuth(appKey, clock)

  val ws = WebSocket(oauth, host, port, pollInterval, client, clock)
  def static(file: String, request: Request) = {
    StaticFile.fromResource("/" + file, Some(request)).map(Task.now).getOrElse(NotFound())
  }

  val authed: Kleisli[Task, (User, Request), Response] = Kleisli({ case (user, request) => println(request); request match {
    case GET -> Root => Ok(s"Hello $user")
    case GET -> Root / path if List(".js", ".css", ".map", ".html").exists(path.endsWith) =>
      static(path, request)
    case GET -> Root / "fleet-ws" / fleetId => ws(user, fleetId)
    case GET -> Root / "fleet" / fleetId => static("fleet.html", request)
    case _ => NotFound()
  }})

  val service: HttpService = HttpService({
    oauthservice.orElse(PartialFunction{ r: Request => {
      oauthauth.maybeAuth(r)
        .map(getUser).sequence.map(_.flatten)
        .flatMap({_ match {
          case Some(user) => authed local {x: Request => (user, x)} run r
          case None => Found(Uri(path="/" + oauthClientSettings.loginPath))
        }})
    }}).orElse(PartialFunction(_ => NotFound()))
  })

  val builder = BlazeBuilder.mountService(service)
  val server = builder.bindHttp(port, host)
}

object Loader extends ServerApp {
  implicit val configuredUri = Configured[String].flatMap(s => Configured(_ => Uri.fromString(s).toOption))
  val xa = DriverManagerTransactor[Task](buildInfo.BuildInfo.flywayDriver, buildInfo.BuildInfo.flywayUrl, buildInfo.BuildInfo.flywayUser, buildInfo.BuildInfo.flywayPassword)
  def server(args: List[String]): Task[Server] = {
    buddy.map(_.server.run)
  }

  def buddy: Task[FleetBuddy] = {
    val config = knobs.loadImmutable(Required(ClassPathResource("application.conf")) :: Required(ClassPathResource("secrets.conf")) :: Nil)
    for {
      cfg <- config
      clientId = cfg.require[String]("eveonline.clientID")
      clientSecret = cfg.require[String]("eveonline.clientSecret")
      key = cfg.require[String]("secret")
      host = cfg.lookup[String]("host")
      port = cfg.lookup[Int]("port")
      callback = cfg.require[Uri]("eveonline.callback")
      pollInterval = cfg.require[Duration]("poll-interval")
    } yield {
      val h = host.getOrElse("localhost")
      val p = port.getOrElse(9000)
      val secret = PrivateKey(scala.io.Codec.toUTF8(key))
      FleetBuddy(
        OAuth2Settings(
          uri("https://login.eveonline.com/oauth/authorize"),
          uri("https://login.eveonline.com/oauth/token"),
          callback,
          uri("https://login.eveonline.com/oauth/verify"),
          clientId,
          clientSecret,
          uri("https://login.eveonline.com/oauth/token"),
          Some("fleetRead fleetWrite")
        ), h, p, secret, pollInterval, xa)
    }
  }
}
