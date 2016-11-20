import scalaz._, Scalaz._
import scalaz.concurrent.Task
import doobie.contrib.specs2.analysisspec.AnalysisSpec
import org.specs2.mutable.Specification
import java.time.{ Instant, Clock }

import doobie.imports._
import org.http4s._, org.http4s.dsl._, org.http4s.client._, org.http4s.util.CaseInsensitiveString

import eveapi._
import models._
import oauth._
import eveapi.compress.CompressedFleet
import eve._

object FleetHistorySpec extends Specification {
  val transactor = DriverManagerTransactor[Task](buildInfo.BuildInfo.flywayDriver, buildInfo.BuildInfo.flywayUrl + "test", buildInfo.BuildInfo.flywayUser, buildInfo.BuildInfo.flywayPassword)
  val user = User(1234, "foo", OAuth2Token("AT", "TT", 3600, "RT"))

  val fleet = """{"isVoiceEnabled": false, "motd": "", "isFreeMove": false, "isRegistered": false, "members": {"href": "https://crest-tq.eveonline.com/fleets/1022511257640/members/"}, "wings": {"href": "https://crest-tq.eveonline.com/fleets/1022511257640/wings/"}}"""
  val wings = """{"totalCount_str": "1", "items": [{"name": "Wing 1", "href": "https://crest-tq.eveonline.com/fleets/1022511257640/wings/2027711257640/", "squadsList": [{"id_str": "3051911257640", "href": "https://crest-tq.eveonline.com/fleets/1022511257640/wings/2027711257640/squads/3051911257640/", "id": 3051911257640, "name": "Squad 1"}], "id_str": "2027711257640", "squads": {"href": "https://crest-tq.eveonline.com/fleets/1022511257640/wings/2027711257640/squads/"}, "id": 2027711257640}], "pageCount": 1, "pageCount_str": "1", "totalCount": 1}"""
  val members = """{"totalCount_str": "1", "items": [{"takesFleetWarp": true, "squadID": 3051911257640, "solarSystem": {"id_str": "30003833", "href": "https://crest-tq.eveonline.com/solarsystems/30003833/", "id": 30003833, "name": "Oulley"}, "wingID": 2027711257640, "boosterID_str": "3", "roleID": 3, "character": {"isNPC": false, "id_str": "93709888", "href": "https://crest-tq.eveonline.com/characters/93709888/", "id": 93709888, "name": "Rectar en Meunk"}, "boosterID": 3, "boosterName": "Squad Booster", "href": "https://crest-tq.eveonline.com/fleets/1022511257640/members/93709888/", "squadID_str": "3051911257640", "roleName": "Squad Commander (Boss)", "station": {"id_str": "60011836", "href": "https://crest-tq.eveonline.com/stations/60011836/", "id": 60011836, "name": "Oulley IV - Federation Navy Assembly Plant"}, "ship": {"id_str": "37482", "href": "https://crest-tq.eveonline.com/inventory/types/37482/", "id": 37482, "name": "Stork"}, "joinTime": "2016-08-20T12:22:56", "wingID_str": "2027711257640", "roleID_str": "3"}], "pageCount": 1, "pageCount_str": "1", "totalCount": 1}"""

  val fleetUri = Uri.uri("https://crest-tq.eveonline.com/fleets/1022511257640/")
  val wingsUri = Uri.uri("https://crest-tq.eveonline.com/fleets/1022511257640/wings/")
  val membersUri = Uri.uri("https://crest-tq.eveonline.com/fleets/1022511257640/members/")

  "Saving a state should not throw an error" >> {
    val state = ApiStream.fleetState(Uri.uri("https://crest-tq.eveonline.com/fleets/1022511257640/")).foldMap(Execute.localInterpreter({
      case `fleetUri` => fleet
      case `wingsUri` => wings
      case `membersUri` => members
    })).map(_.run(Clock.systemUTC)).fold(err => throw new Exception(err.toString), x => x)
    User.upsert(user).transact(transactor).unsafePerformSync
    FleetHistory.insert(user, state).transact(transactor).attempt.unsafePerformSync.fold({ err =>
      err match {
        case b: java.sql.BatchUpdateException =>
          throw new Exception(s"$err\n${b.getNextException}")
        case _ => throw err
      }
    }, x => x)
    ok
  }
}
