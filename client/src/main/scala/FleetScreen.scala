package main

import cats.data.Xor
import diode._
import eveapi._
import org.scalajs.dom.ext.Ajax
import scala.concurrent.ExecutionContext

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw._
import scalatags.JsDom.all._
import scala.scalajs.js.annotation.{JSExport, JSExportDescendentObjects}
import org.reactormonk.Counter
import io.circe._, io.circe.generic._, io.circe.parser._, io.circe.syntax._
import scala.concurrent.Future


sealed trait FleetAction extends Action
case class WSMessage(message: ServerToClient) extends FleetAction
case class DisplayFleetUpdates(update: List[FleetEvent]) extends FleetAction

case class FleetScreen(initialModel: FleetState, root: Element, fleetId: String, ec: ExecutionContext) extends Screen[FleetState] {
  implicit val e = ec
  val actionHandler: HandlerFunction = { (m, a) =>
    a match {
      case a: FleetAction => ah(m, a)
      case _ => throw new IllegalStateException(s"Unexpected action: $a")
    }
  }

  subscribe(zoom(identity))({m =>
    U.clear(root)
    render
  })

  def ah(model: FleetState, action: FleetAction): Option[ActionResult[FleetState]] = {
    action match {
      case WSMessage(msg) => Some(handleMessage(msg))
      case DisplayFleetUpdates(updates) => None // TODO
    }
  }

  def handleMessage(msg: ServerToClient): ActionResult[FleetState] = {
    msg match {
      case FleetUpdate(state, updates) => ActionResult(
        Some(state),
        Some(Effect(Future(DisplayFleetUpdates(updates))))
      )
    }
  }

  def wsUri(fleetId: String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    (s"$wsProtocol://${dom.document.location.host}/fleet-ws/$fleetId")
  }

  val websocket = new WebSocket(wsUri(fleetId))
  websocket.onopen = { (event: Event) =>
    println("Connected!")
  }
  websocket.onerror = { (event: ErrorEvent) =>
    println(s"Error: $event")
  }
  websocket.onmessage = { (event: MessageEvent) =>
    val msg = decode[ServerToClient](event.data.toString).fold(err => println(s"Invalid message: $event"), x => x)
    msg match {
      case msg: FleetUpdate => apply(WSMessage(msg))
    }
  }
  websocket.onclose = { (event: CloseEvent) =>
    println(s"Error: $event")
  }

  def shipImg(id: String, size: Int) = img(src := s"https://image.eveonline.com/Render/${id}_$size.png")

  def render = {
    val ships = Counter(zoom(identity).value.members.map(_.ship.id.toString))
    val list = ul(ships.toMap.map({ case (id, count) =>
      li(p(shipImg(id, 128), s"x $count"))
    }).toList)
    root.innerHTML = list.toString
    // root.appendChild(ul(update.events.map(e => li(e.toString))).render)
  }
}

object FleetScreen {
  def apply(fleetId: String, root: Element)(implicit ex: ExecutionContext): Future[Xor[io.circe.Error, FleetScreen]] = {
    Ajax.get(s"/fleet-ws/$fleetId").map({resp =>
      decode[FleetState](resp.responseText).map(state => FleetScreen(state, root, fleetId, ex))
    })
  }
}
