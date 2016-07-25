package utils

import argonaut._, argonaut.Argonaut._, argonaut.Shapeless._
import java.lang.NumberFormatException
import java.time.Clock
import org.http4s.Uri
import org.http4s.Uri.Authority
import org.http4s.util.CaseInsensitiveString
import scala.concurrent.duration.Duration
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._
import scalaz.stream.{Exchange, Process, Sink}
import java.util.concurrent.ScheduledExecutorService
import scalaz.concurrent.Task
import scalaz._

import models._
import oauth._
import shared._
import eveapi._
import eveapi.oauth._
import OAuth2.Api
import eveapi.utils.Decoders._

case class EveServer(server: Uri.RegName)

case class WebSocket(pollInterval: Duration, oauth: OAuth2, server: EveServer) {
  // javascript numbers are max. 53 bit, Longs are longer.
  implicit val longToString: CodecJson[Long] = CodecJson(
    (l: Long) => Json.jString(l.toString),
    c => c.as[String].flatMap(str =>
      \/.fromTryCatchNonFatal(str.toLong).fold(err => err match {
        case e: NumberFormatException => DecodeResult.fail(e.toString, c.history)
        case e => throw e
      },
        DecodeResult.ok
      )
    )
  )
  def fleetUri(id: Long, server: EveServer) = Uri(scheme = Some(CaseInsensitiveString("https")), authority = Some(Authority(host=server.server)), path = s"/fleets/$id/")

  def apply(user: User, fleetId: Long)(implicit s: ScheduledExecutorService) = {
    val source: Process[Api, FleetState] = ApiStream.fleetPollSource(fleetUri(fleetId, server), pollInterval, Execute.OAuthInterpreter)
    val serverToClient: Process[Task, ServerToClient] = ApiStream.toClient(source).translate[Task](ApiStream.fromApiStream(oauth, user.token))
    val websocketProtocol: Process[Task, Text] = serverToClient.map(m => Text(m.asJson.nospaces))
    val fromClient = ApiStream.fromClient.contramap[WebSocketFrame]({
      case Text(t, _) => Parse.decodeEither[ClientToServer](t).fold(err => throw new IllegalArgumentException(s"Invalid json: $t"), x => x)
      case x => throw new IllegalArgumentException(s"Unexpected message: ${x}")
    })
    WS(Exchange(websocketProtocol, fromClient))
  }
}
