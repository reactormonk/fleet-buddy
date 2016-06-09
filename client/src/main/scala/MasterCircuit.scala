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

sealed trait MasterAction extends Action
case class ActivateModule(module: Module) extends MasterAction
case class ErrorLoadingModule(error: String) extends MasterAction
case class LoadFleet(id: String) extends MasterAction
case class ReturnToMenu(error: Option[String]) extends MasterAction

sealed trait Module {
  def unload: Unit = ()
  def render: Unit
  def href: String
}

trait Screen[T <: AnyRef] extends Circuit[T] {
  def render: Unit
}

sealed trait ScreenModule[T <: AnyRef] extends Module {
  def screen: Screen[T]
  def render = screen.render
  def href: String
}
case class FleetModule(screen: FleetScreen, href: String) extends ScreenModule[FleetState]
case class MainMenuModule(screen: MainMenuScreen, href: String) extends ScreenModule[Option[String]]

case class MasterCircuit(initialModel: Module, root: Element, ec: ExecutionContext) extends Circuit[Module] {
  val actionHandler: (Module, Action) => Option[ActionResult[Module]] = { (m, a) =>
    a match {
      case a: MasterAction => ah(m, a)
      case _ => throw new IllegalStateException(s"Unexpected action: $a")
    }
  }

  def render = ()

  subscribe(zoom(identity))({m =>
    U.clear(root)
    dom.window.location.href = m.value.href
    m.value.render
  })

  def ah(module: Module, action: MasterAction): Option[ActionResult[Module]] = {
    implicit val e = ec
    module.unload
    action match {
      case LoadFleet(id) =>
        Some(ActionResult.EffectOnly(Effect(FleetScreen(id, root).map[MasterAction]({
          case Xor.Right(circuit) =>
            ActivateModule(FleetModule(circuit, s"/fleet/$id"))
          case Xor.Left(error) =>
            ErrorLoadingModule(error.toString)
        }))))
      case ReturnToMenu(error: Option[String]) => {
        Some(ActionResult.EffectOnly(Effect(Future(ActivateModule(MainMenuModule(MainMenuScreen(error, root), "/"))))))
      }
      case ActivateModule(module) => {
        Some(ActionResult.ModelUpdate(module))
      }
      case ErrorLoadingModule(error) =>
        Some(ActionResult.EffectOnly(Effect(Future(ReturnToMenu(Some(error))))))
    }
  }
}

// object MasterCircuit {
//   def apply(root: Element, href: String)(implicit ec: ExecutionContext): MasterCircuit = {
//     val module = href.split("/") match {
//       case Array("") => MainMenuScreen(None, root, ec)
//       case Array("", "fleet", id) => FleetScreen(id, root, ec)
//     }
//     MasterCircuit(module, root, ec)
//   }
// }
