import doobie.imports._
import scalaz._, Scalaz._
import utils._
import org.scalacheck._
import scalaz.concurrent.Task
import org.specs2._
import shared.FleetState

object GenSpec extends Specification with ScalaCheck {
  val transactor = DriverManagerTransactor[Task](buildInfo.BuildInfo.flywayDriver, buildInfo.BuildInfo.flywayUrl + "test", buildInfo.BuildInfo.flywayUser, buildInfo.BuildInfo.flywayPassword)
  val gen = FleetGen().transact(transactor).unsafePerformSync
  import gen._
  val fleetGenSpec = prop { (gen: FleetState) => gen == gen }
  def is = s2"FleetGen should generate states $fleetGenSpec"
}
