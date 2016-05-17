package utils

import java.time.Clock
import scala.concurrent.duration.Duration
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._
import org.http4s._, org.http4s.dsl._, org.http4s.server._
import org.http4s.Uri.{ Authority, RegName }
import org.http4s.client.Client
import org.http4s.util.CaseInsensitiveString
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import scalaz.stream.Exchange
import java.util.concurrent.ScheduledExecutorService

import models._
import eveapi._
import oauth._

case class WebSocket(oauth: OAuth2Settings, host: String, port: Int, pollInterval: Duration, client: Client, clock: Clock) {

  def fleetUri(id: String) = Uri(scheme = Some(CaseInsensitiveString("https")), authority = Some(Authority(host=Uri.RegName("crest-tq.eveonline.com"))), path = s"/fleets/$id/")

  def apply(user: User, fleetId: String)(implicit s: ScheduledExecutorService) = {
    val toClient = ApiStream.toClient(fleetUri(fleetId), pollInterval).translate(ApiStream.fromApiStream(oauth, client, clock, user.token)).map(m => Text(m.asJson.noSpaces))
    val fromClient = ApiStream.fromClient
    WS(Exchange(toClient, fromClient.contramap({
      case Text(t, _) => decode[ClientToServer](t).fold(err => throw new IllegalArgumentException(s"Invalid json: $t"), x => x)
      case x => throw new IllegalArgumentException(s"Unexpected message: ${x}")
    })))
  }
}
