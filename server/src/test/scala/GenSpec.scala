import doobie.imports._
import scalaz._, Scalaz._
import shared.FleetUpdates
import utils._
import org.scalacheck._
import scalaz.concurrent.Task
import org.specs2._

object GenSpec extends Specification with ScalaCheck {
  val gen = God.fleetGen
  import gen._
  val fleetGenSpec = prop { (gen: FleetUpdates) => gen == gen }
  def is = s2"FleetGen should generate states $fleetGenSpec"
}
