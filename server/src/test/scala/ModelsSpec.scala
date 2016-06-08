import doobie.imports._
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import doobie.contrib.specs2.analysisspec.AnalysisSpec
import org.specs2.mutable.Specification

import models._
import oauth._

object UserSpec extends Specification with AnalysisSpec {
  val transactor = DriverManagerTransactor[Task](buildInfo.BuildInfo.flywayDriver, buildInfo.BuildInfo.flywayUrl + "test", buildInfo.BuildInfo.flywayUser, buildInfo.BuildInfo.flywayPassword)
  val user = User(1234, "foo", OAuth2Token("AT", "TT", 3600, "RT"))
  check(User.upsertQuery(user))
  check(User.selectQuery(2L))
  check(User.selectQuery("foo"))
  check(User.charInsertSql)
  check(FleetHistory.memberStatusSql)
  check(FleetHistory.wingStatusSql)
  check(FleetHistory.squadStatusSql)
  check(FleetHistory.stationSql)
  check(FleetHistory.solarSystemSql)
  check(FleetHistory.shipSql)
  check(FleetHistory.selectMembers)
  check(FleetHistory.selectWings)
  check(FleetHistory.selectFleets)
}
