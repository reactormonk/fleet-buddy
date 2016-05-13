package controllers

import org.atnos.eff._, org.atnos.eff.syntax.all._, org.atnos.eff.all._
import effects._
import TaskEffect._
import java.time.Clock
import knobs.{ CfgText, Configured }
import knobs.{Required, ClassPathResource, Config}
import scalaz._, Scalaz._
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import org.http4s._, org.http4s.dsl._, org.http4s.server._
import org.http4s.Uri.{ Authority, RegName }
import org.http4s.client.Client
import org.http4s.server.blaze._

import eveapi._
import oauth._
import effects._
import errors._
import models._

case class FleetBuddy(oauth: OAuth2Settings, host: String, port: Int) {
  val seed = new java.security.SecureRandom().nextLong
  val clock = Clock.systemUTC()
  val oauthState = OAuth2State(seed)
  val oauthClientSettings = OAuth2ClientSettings("login")
  val client = org.http4s.client.blaze.PooledHttp1Client()

  val db = scala.collection.concurrent.TrieMap[Long, User]()
  val storeToken: OAuth2Token => Task[Response] = { token =>
    val verified: Task[Err \/ (VerifyAnswer, OAuth2Token)] = EveApi.verify.runReader(clock).runReader(oauth).runReader(client).runState(token).runDisjunction.detach
    verified.flatMap({ _ match {
      case -\/(err) => {
        err.printStackTrace
        InternalServerError(err.toString)
      }
      case \/-((answer, token)) => {
        val user = User(answer.CharacterID, answer.CharacterName, token)
        db += user.id -> user
        Ok(s"Welcome $user.name")
      }
    }})
  }
  val oauthserviceTask: Eff[(Err \/ ?) |: Task |: NoEffect, HttpService] =
    OAuth2.oauthService[Reader[Client, ?] |: Reader[OAuth2Settings, ?] |: Reader[OAuth2State, ?] |: Reader[Clock, ?] |: Reader[OAuth2ClientSettings, ?] |: (Err \/ ?) |: Task |: NoEffect](storeToken)
      .runReader(clock).runReader(oauth).runReader(oauthClientSettings).runReader(oauthState).runReader(client)
  val oauthservice: HttpService = oauthserviceTask.runDisjunction.detach.run.fold(err => throw new IllegalArgumentException(s"Loading failed: $err"), x => x)
  val builder = BlazeBuilder.mountService(oauthservice)
  val server = builder.bindHttp(port, host)
}

object Loader extends ServerApp {
  implicit val configuredUri: Configured[Uri] = Configured(_ match {
    case CfgText(text) => Uri.fromString(text).toOption
    case _ => None
  })
  def server(args: List[String]): Task[Server] = {
    val config = knobs.loadImmutable(Required(ClassPathResource("application.conf")) :: Required(ClassPathResource("secrets.conf")) :: Nil)
    val buddy: Task[FleetBuddy] = for {
      cfg <- config
      clientId = cfg.require[String]("eveonline.clientID")
      clientSecret = cfg.require[String]("eveonline.clientSecret")
      host = cfg.lookup[String]("host")
      port = cfg.lookup[Int]("port")
      callback = cfg.lookup[Uri]("eveonline.callback")
    } yield {
      val h = host.getOrElse("localhost")
      val p = port.getOrElse(9000)
      val cb = callback.getOrElse(Uri(path="callback", authority = Some(Authority(host=Uri.RegName(h), port=Some(p)))))
      FleetBuddy(
        OAuth2Settings(
          uri("https://login.eveonline.com/oauth/authorize"),
          uri("https://login.eveonline.com/oauth/token"),
          cb,
          uri("https://login.eveonline.com/oauth/verify"),
          clientId,
          clientSecret,
          uri("https://login.eveonline.com/oauth/token"),
          Some("fleetRead fleetWrite")
        ), h, p)
    }
    buddy.map(_.server.run)
  }
}
