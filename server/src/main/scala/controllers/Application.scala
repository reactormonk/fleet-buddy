package controllers

import argonaut.Argonaut._
import eveapi.errors.EveApiError
import org.atnos.eff._, org.atnos.eff.syntax.all._, org.atnos.eff.all._
import java.time.Clock
import knobs.{Required, FileResource, Config, CfgText, Configured}
import scala.concurrent.duration.Duration
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import org.http4s._, org.http4s.dsl._, org.http4s.server._, org.http4s.argonaut._
import org.http4s.Uri.{ Authority, RegName }
import org.http4s.client.Client
import org.http4s.server.blaze._
import doobie.imports._
import org.log4s.getLogger
import org.reactormonk.PrivateKey
import java.time.Instant

import utils._
import oauth._
import effects._
import errors._
import models._
import eve._
import utils.codecs._

import eveapi.oauth._
import eveapi.utils.TaskEffect._

case class FleetBuddy(settings: OAuth2Settings, host: String, port: Int, appKey: PrivateKey, pollInterval: Duration, xa: Transactor[Task], eveserver: EveServer, gen: FleetGen) {
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
  val anon = User(0, "Anon", OAuth2Token("", "", 0, "", Instant.MIN))

  def static(file: String, request: Request) = {
    StaticFile.fromResource("/" + file, Some(request)).map(Task.now).getOrElse(NotFound())
  }

  val authed: Kleisli[Task, (User, Request), Response] = Kleisli({ case (user, request) => request match {
    case GET -> Root / "api" / "fleet-ws" / LongVar(fleetId) => {
      val topic = topics(user, fleetId)
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
    case GET -> Root / "api" / "fleetstate" / LongVar(fleetId) / InstantVar(instant) => {
      FleetHistory.loadNext(fleetId, user, instant).transact(xa).flatMap({
        case Some(state) => Ok(state.asJson)
        case None => NotFound()
      })
    }
    case _ -> s if s.startsWith(Path("/api")) => NotFound()
    case GET -> _ => static("index.html", request)
    case _ => NotFound()
  }})

  val service: HttpService = HttpService({
    ({
      case request @ GET -> Root / "favicon.ico" => static("favicon.ico", request)
      case GET -> Root / "api" / "fleetstate" / "random" => {
        gen.state.sample match {
          case Some(value) => Ok(value.asJson)
          case None => NotFound()
        }
      }
      case GET -> Root / "sample" => {
        Option(getClass.getResource("/client/sample.html")).map({res =>
          Ok(io.Source.fromURL(res).mkString.replace("{{sample}}", gen.state.sample.asJson.nospaces))
        }).getOrElse(NotFound())
      }
      case request @ GET -> Root / path if List(".js", ".css", ".map", ".html", ".webm").exists(path.endsWith) =>
        static(path, request)
      case request @ _ -> s if ! s.startsWith(Path("/api")) => static("index.html", request)
    }: PartialFunction[Request, Task[Response]])
      .orElse(oauthservice).orElse(PartialFunction{ r: Request => {
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


case class DBConfig(url: String, user: String, password: String, driver: String = "org.postgresql.Driver")

object Loader extends ServerApp {
  implicit val configuredUri = Configured[String].flatMap(s => Configured(_ => Uri.fromString(s).toOption))
  def server(args: List[String]): Task[Server] = {
    buddy.map(_.server.run)
  }

  val load = knobs.loadImmutable(Required(FileResource(sys.Prop[java.io.File]("configfile").option.getOrElse(new java.io.File("application.conf")))):: Nil)

  def db(load: Config): DBConfig = {
    DBConfig(
      load.require[String]("db.url"),
      load.require[String]("db.user"),
      load.require[String]("db.password")
    )
  }

  def buddy: Task[FleetBuddy] = {
    for {
      cfg <- load
      clientId = cfg.require[String]("eveonline.clientID")
      clientSecret = cfg.require[String]("eveonline.clientSecret")
      key = cfg.require[String]("app-secret")
      host = cfg.lookup[String]("host").getOrElse("localhost")
      port = cfg.lookup[Int]("port").getOrElse(9476)
      callback = cfg.require[Uri]("eveonline.callback")
      pollInterval = cfg.require[Duration]("poll-interval")
      dbcfg = db(cfg)
      xa = DriverManagerTransactor[Task](dbcfg.driver, dbcfg.url, dbcfg.user, dbcfg.password)
      fleetGen <- FleetGen().transact(xa)
    } yield {
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
        ), host, port, secret, pollInterval, xa,
        EveServer(Uri.RegName("crest-tq.eveonline.com")), // TODO parameterize this
        fleetGen
      )
    }
  }
}
