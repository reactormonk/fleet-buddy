package controllers

import eveapi.errors.EveApiError
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
import org.log4s.getLogger
import shared.FleetState

import utils._
import oauth._
import effects._
import errors._
import models._
import eve._

import eveapi.oauth._
import eveapi.utils.TaskEffect._

case class FleetBuddy(settings: OAuth2Settings, host: String, port: Int, appKey: PrivateKey, pollInterval: Duration, xa: Transactor[Task], eveserver: EveServer) {
  val seed = new java.security.SecureRandom().nextLong
  val clock = Clock.systemUTC()
  val oauthState = OAuth2State(seed)
  val oauthClientSettings = OAuth2ClientSettings("login")
  val client = org.http4s.client.blaze.PooledHttp1Client()
  implicit val scheduler = scalaz.stream.DefaultScheduler
  val oauth = OAuth2(client, settings, oauthState, clock, oauthClientSettings)

  private[this] val logger = getLogger

  def getUser(id: String): Task[Option[User]] = User.load(id.toLong).transact(xa)
  def addUser(user: User): Task[Unit] = User.upsert(user).transact(xa)

  val storeToken: OAuth2Token => Task[Response] = { token =>
    val verified: Task[Err \/ (VerifyAnswer, OAuth2Token)] =
      OAuth2.verify.runReader(oauth).runState(token).runDisjunction.detach.map(_.leftMap(ApiError.apply))
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

  val oauthservice = oauth.oauthService(storeToken)
  val oauthauth = OAuthAuth(appKey, clock)

  val topics = TopicHolder(pollInterval, oauth, eveserver)
  val dbs = DBHolder(xa)

  def static(file: String, request: Request) = {
    StaticFile.fromResource("/" + file, Some(request)).map(Task.now).getOrElse(NotFound())
  }

  val authed: Kleisli[Task, (User, Request), Response] = Kleisli({ case (user, request) => request match {
    case GET -> Root / path if List(".js", ".css", ".map", ".html").exists(path.endsWith) =>
      static(path, request)
    case GET -> Root / "api" / "fleet-ws" / fleetId => {
      val topic = topics(user, fleetId.toLong)
      val toDB = dbs(user, topic.subscribe.collect({case \/-(s) => s}))
      Task.fork(toDB.run).unsafePerformAsync(_.fold({err =>
        logger.error(s"Error from DB: $err")
        err match {
          case e: java.sql.BatchUpdateException =>
            logger.error(e.getNextException.toString)
        }
      }, x => x))
      WebSocket(topic.subscribe)
    }
    case _ -> s if s.startsWith(Path("/api")) => NotFound()
    case GET -> _ => static("index.html", request)
    case _ => NotFound()
  }})

  val favicon: PartialFunction[Request, Task[Response]] = { case request @ GET -> Root / "favicon.ico" => static("favicon.ico", request)}

  val service: HttpService = HttpService({
    favicon.orElse(oauthservice).orElse(PartialFunction{ r: Request => {
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
        ), h, p, secret, pollInterval, xa,
        EveServer(Uri.RegName("crest-tq.eveonline.com")) // TODO parameterize this
      )
    }
  }
}
