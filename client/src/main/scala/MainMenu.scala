package main

import cats.data.Xor
import scala.concurrent.ExecutionContext
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw._
import scala.util.{Success, Failure}
import scalatags.JsDom.all._
import scala.scalajs.js.annotation.{JSExport, JSExportDescendentObjects}
import diode._
import scala.concurrent.Future

import eveapi._

sealed trait MainMenuAction

case class MainMenuScreen(initialModel: Option[String], root: Element) extends Screen[Option[String]] {
  val fleetSelect =
    form(cls := "ui form")(
      div(cls := "field", onsubmit := { event: Event =>
        val url = event.currentTarget.asInstanceOf[HTMLFormElement].childNodes(1).asInstanceOf[HTMLInputElement].value
        """(\d+)""".r.findFirstIn(url) match {
          case Some(id) => apply(LoadFleet(id))
          case None => apply(ReturnToMenu(Some(s"Fleet Id not found in $url")))
        }
        false
      })(
        label("Fleet URL"),
        input(cls := "fleet-url", `type` := "text", name := "fleet-url", placeholder := "Fleet URL")
      ),
      button(cls := "ui button", `type` := "submit")("Get rolling!")
    )

  def errorMessage(error: String) =
    div(cls := "ui negative message")(
      i(cls := "close icon"),
      div(cls := "header")(
        error
      )
    )

  val actionHandler: (Option[String], Action) => Option[ActionResult[Option[String]]] = { (m, a) =>
    a match {
      case a: MainMenuAction => ah(m, a)
      case _ => throw new IllegalStateException(s"Unexpected action: $a")
    }
  }

  def ah(m: Option[String], action: MainMenuAction) = None

  def render = {
    U.clear(root)
    zoom(identity).apply().foreach(e => root.appendChild(errorMessage(e).render))
    root.appendChild(fleetSelect.render)
  }
}

