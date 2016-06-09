package main

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw._

object U {
  def clear(root: Element): Unit = {
    while (root.hasChildNodes()) {
      root.removeChild(root.lastChild)
    }
  }
}
