package main

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw._
import io.circe._, io.circe.generic._, io.circe.parser._, io.circe.syntax._
import scalatags.JsDom.all._
import org.reactormonk.Counter
import scala.scalajs.js.annotation.{JSExport, JSExportDescendentObjects}

import eveapi._

object FleetBuddy extends js.JSApp {
  @JSExport
  def main(): Unit = {
    val fleetId = dom.window.location.pathname.split("/").last
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
        case msg: FleetState => updateFleetDisplay(msg)
      }
    }
    websocket.onclose = { (event: CloseEvent) =>
      println("Error: $event")
    }
  }

  def shipImg(id: String, size: Int) = img(s"https://image.eveonline.com/Render/${id}_$size.png")

  def wsUri(fleetId: String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    (s"$wsProtocol://${dom.document.location.host}fleet-ws/$fleetId")
  }

  def updateFleetDisplay(state: FleetState) = {
    val ele = dom.window.document.getElementById("fleet-overview")
    val ships = Counter(state.members.map(_.ship.id_str))
    val list = ul(ships.toMap.map({ case (id, count) =>
      li(p(shipImg(id, 128), s"x $count"))
    }).toList)
    ele.innerHTML = list.toString
  }
}
