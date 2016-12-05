import doobie.imports._
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import doobie.contrib.specs2.analysisspec.AnalysisSpec
import org.specs2.mutable.Specification
import java.time.Instant

import eveapi._
import models._
import oauth._
import eveapi.compress.CompressedFleet

object UserSpec extends Specification with AnalysisSpec {
  val transactor = God.xa
  val user = User(1234, "foo", OAuth2Token("AT", "TT", 3600, "RT"))
  check(User.upsertQuery(user))
  check(User.selectQuery(2L))
  check(User.selectQuery("foo"))
  check(User.charInsertSql)
  check(FleetHistory.memberStatusSql)
  check(FleetHistory.wingStatusSql)
  check(FleetHistory.squadStatusSql)
  check(FleetHistory.selectMembers)
  check(FleetHistory.selectWings)
  check(FleetHistory.selectFleets)
  check(FleetHistory.fleetInsertQuery(user, CompressedFleet(0, false, false, false, ""), Instant.now))
  check(StaticData.allShips)
  check(StaticData.allSystems)
  check(StaticData.allStations)
}
