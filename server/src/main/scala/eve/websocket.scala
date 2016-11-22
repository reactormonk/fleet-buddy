package eve

import argonaut._, argonaut.Argonaut._, argonaut.ArgonautShapeless._
import java.lang.NumberFormatException
import org.http4s.{ Response, Uri }
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._
import scalaz.stream.{Exchange, Process, Sink}
import scalaz.concurrent.Task
import scalaz._

import models._
import oauth._
import shared._
import eveapi._
import eveapi.oauth._
import eveapi.errors.EveApiError
import eveapi.utils.Decoders._

object WebSocket {
  def apply(process: Process[Task, EveApiError \/ FleetState]): Task[Response] = {
    val serverToClient: Process[Task, ServerToClient] = process.pipe(ApiStream.toClient.liftR[EveApiError]).map(_.fold(err => throw err, x => x))
    val websocketProtocol: Process[Task, Text] = serverToClient.map(m => Text(m.asJson.nospaces))
    val fromClient = ApiStream.fromClient.contramap[WebSocketFrame]({
      case Text(t, _) => Parse.decodeEither[ClientToServer](t).fold(err => throw new IllegalArgumentException(s"Invalid json: $t"), x => x)
      case x => throw new IllegalArgumentException(s"Unexpected message: ${x}")
    })
    WS(Exchange(websocketProtocol, fromClient))
  }
}
