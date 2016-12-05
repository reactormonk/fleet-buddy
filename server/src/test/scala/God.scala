import doobie.imports._
import eve.EveServer
import eveapi.oauth.OAuth2Settings
import org.http4s._, org.http4s.dsl._, org.http4s.server._, org.http4s.argonaut._
import scala.concurrent.duration._
import controllers.FleetBuddy
import scalaz.concurrent.Task

object God {
  val xa = DriverManagerTransactor[Task](buildInfo.BuildInfo.flywayDriver, buildInfo.BuildInfo.flywayUrl + "test", buildInfo.BuildInfo.flywayUser, buildInfo.BuildInfo.flywayPassword)
  val fleetGen = utils.FleetGen().transact(xa).unsafePerformSync

  def appClient = FleetBuddy(
    OAuth2Settings(
      uri("https://localhost/"),
      uri("https://localhost/"),
      uri("https://localhost/"),
      uri("https://localhost/"),
      "",
      "",
      uri("https://localhost/"),
      Some("")
    ), "localhost", 7777, org.reactormonk.PrivateKey("sekrit".toArray.map(_.toByte)), 5.seconds, xa,
    EveServer(Uri.RegName("crest-tq.eveonline.com")),
    fleetGen
  )
}
