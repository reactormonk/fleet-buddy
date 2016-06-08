package models
import oauth._
import doobie.imports._
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import scalaz.stream.Process
import shapeless._, shapeless.poly._, shapeless.contrib.scalaz._
import java.time._
import utils.Doobie._
import java.time.Instant

import eveapi._

case class CleanMemberState(
  parent: Long,
  boosterId: Short,
  joinTime: Instant,
  roleId: Short,
  characterId: Long,
  shipId: Long,
  solarSystem: Long,
  stationId: Option[Long],
  squadId: Long,
  takesFleetWarp: Boolean,
  wingId: Long
)

object FleetHistory {
  def fleetInsertQuery(s: Fleet, recorded: Instant) = {
    sql"""
insert into fleetstate
  (isFreeMove, isRegistered, isVoiceEnabled, motd, recorded)
values
  (${s.isFreeMove}, ${s.isRegistered}, ${s.isVoiceEnabled}, ${s.motd}, recorded)
""".update
  }

  val memberStatusSql = Update[CleanMemberState]("""
insert into memberstatus
(
  parentstatus,
  boosterId,
  joinTime,
  roleId,
  characterId,
  shipId,
  solarSystemId,
  stationId,
  squadId,
  takesFleetWarp,
  wingId
) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""")

  val wingStatusSql = Update[(Long, Long, String)]("""
insert into wingstatus
(
  parentstatus,
  id,
  name
) values (?, ?, ?)
""")

  val squadStatusSql = Update[(Long, Long, Long, String)]("""
insert into squadstatus
(
  parentstatus,
  wingid,
  id,
  name
) values (?, ?, ?, ?)
""")

  def idTemplate[T](table: String) = Update[eveapi.Id[T]](s"""
insert into ${table}
(
  id,
  name
) values (?, ?)
on conflict do nothing
""")

  val stationSql = idTemplate[Station]("stations")
  val solarSystemSql = idTemplate[SolarSystem]("solarsystems")
  val shipSql = idTemplate[Ship]("ships")

  def insert(s: FleetState, recorded: Instant) = {
    for {
      _ <- User.charInsertSql.updateMany(s.members.map(_.character))
      _ <- stationSql.updateMany(s.members.flatMap(_.station))
      _ <- solarSystemSql.updateMany(s.members.map(_.solarSystem))
      _ <- shipSql.updateMany(s.members.map(_.ship))
      id <- fleetInsertQuery(s.fleet, recorded).withUniqueGeneratedKeys[Long]("id")
      _ <- wingStatusSql.updateMany(s.wings.map(w => (id, w.id, w.name)))
      _ <- squadStatusSql.updateMany(s.wings.flatMap(w => w.squadsList.map(s => (id, w.id, s.id, s.name))))
      _ <- memberStatusSql.updateMany(
        s.members.map(m => CleanMemberState(
            id,
            m.boosterID,
            Instant.parse(m.joinTime),
            m.roleID,
            m.character.id,
            m.ship.id,
            m.solarSystem.id,
            m.station.map(_.id),
            m.squadID,
            m.takesFleetWarp,
            m.wingID
        )))
    } yield ()
  }

  object toOption extends Poly1 {
    implicit def caseAll[T] = at[T](_.some)
  }

  implicit val maybeStation = implicitly[Composite[Option[Long] :: Option[String] :: HNil]].xmap({ maybeHList =>
    sequence(maybeHList).map({hlist => Generic[eveapi.Id[Station]].from(hlist) })
  }, { station: Option[eveapi.Id[Station]] =>
    station.map({s => Generic[eveapi.Id[Station]].to(s) }) match {
      case Some(h) => h.map(toOption)
      case None => None :: None :: HNil
    }
  })

  val selectMembers = Query[Long, Member]("""
select
  boosterID,
  characters.id, characters.name,
  to_char(joinTime at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
  roleID,
  ships.id, ships.name,
  solarsystems.id, solarsystems.name,
  squadID,
  stations.id, stations.name,
  takesFleetWarp,
  wingID
from memberstatus
 join ships on ships.id = shipid
 join solarsystems on solarsystems.id = solarsystemid
 left join stations on stations.id = stationId
 join characters on characters.id = characterId
where
 parentstatus = ?
""")

  val selectWings = Query[Long, (Long, String, Squad)]("""
select
  wingstatus.id,
  wingstatus.name,
  squadstatus.id,
  squadstatus.name
from
 wingstatus, squadstatus
where
 wingstatus.parentstatus = ? AND
 squadstatus.parentstatus = wingstatus.parentstatus AND squadstatus.wingid = wingstatus.id
""")

  val selectFleets = Query[(Long, Instant, Instant), (Instant, Long, Fleet)]("""
select
  recorded,
  serial_id,
  id,
  isFreeMove,
  isRegistered,
  isVoiceEnabled,
  motd
from fleetstatus
where id = ?
and recorded between ? and ?
""")

  def load(id: Long, from: Instant = Instant.MIN, to: Instant = Instant.MAX): Process[ConnectionIO, FleetState] = {
    selectFleets.process((id, from, to)).flatMap({ case (recorded, serial, fleet) =>
      val res = for {
        members <- selectMembers.to[List](serial)
        wings <- selectWings.to[List](serial).map({
          _.groupBy(_._1).map({ case (k, values) =>
            Wing(values.head._1, values.head._2, values.map(_._3))
          }).toList
        })
      } yield FleetState(fleet, members, wings)
      Process.eval(res)
    })
  }
}
