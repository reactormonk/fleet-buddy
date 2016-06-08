package eveapi

import java.time._

import io.circe._, io.circe.generic._, io.circe.parser._, io.circe.syntax._, io.circe.java8.time._

trait Fetcher[T] {
  type Monad[_]
  type PathGen
  def apply(l: Id[T])(implicit ev: Decoder[T], pg: PathGen): Monad[T]
}

// Even solar systems (8k in count) aren't paginated. Not implemeting for now.
@JsonCodec case class Paginated[T](items: List[T], pageCount: Int, totalCount: Int)

@JsonCodec case class Id[T](id: Long, name: String)

@JsonCodec case class Fleet(
  id: Long,
  isFreeMove: Boolean,
  isRegistered: Boolean,
  isVoiceEnabled: Boolean,
  motd: String
)

@JsonCodec case class Member(
  boosterID: Short,
  character: Id[Character],
  joinTime: String,
  roleID: Short,
  ship: Id[Ship],
  solarSystem: Id[SolarSystem],
  squadID: Long,
  station: Option[Id[Station]],
  takesFleetWarp: Boolean,
  wingID: Long
)

@JsonCodec case class Wing(
  id: Long,
  name: String,
  squadsList: List[Squad]
)

@JsonCodec case class Squad(
  id: Long,
  name: String
)

@JsonCodec case class Character()

@JsonCodec case class SolarSystem()

@JsonCodec case class Station()

@JsonCodec case class Ship()

@JsonCodec case class Location(solarSystem: Id[SolarSystem], station: Option[Id[Station]])
object Location {
  def apply(mem: Member): Location = {
    Location(mem.solarSystem, mem.station)
  }
}

@JsonCodec case class FleetState(fleet: Fleet, members: List[Member], wings: List[Wing])

@JsonCodec sealed trait FleetEvent
sealed trait Change[T] extends FleetEvent {
  def old: T
  def now: T
}
sealed trait Individual extends FleetEvent {
  def id: Id[Character]
}
sealed trait MassChange[T] extends Change[T] {
  def ids: List[Id[Character]]
}

case class MotdChange(old: String, now: String) extends Change[String]
case class FreeMoveChange(old: Boolean, now: Boolean) extends Change[Boolean]
case class RegisteredChange(old: Boolean, now: Boolean) extends Change[Boolean]

case class SquadMove(id: Id[Character], old: Squad, now: Squad) extends Change[Squad] with Individual
case class WingMove(id: Id[Character], old: Wing, now: Wing) extends Change[Wing] with Individual
case class ShipChange(id: Id[Character], old: Id[Ship], now: Id[Ship], where: Location) extends Change[Id[Ship]] with Individual
case class LocationChange(id: Id[Character], old: Location, now: Location) extends Change[Location] with Individual
case class FleetWarpChange(id: Id[Character], old: Boolean, now: Boolean) extends Change[Boolean] with Individual

case class MemberJoin(id: Id[Character], now: Member) extends FleetEvent
case class MemberPart(id: Id[Character], old: Member) extends FleetEvent

case class MassLocationChange(ids: List[Id[Character]], old: Location, now: Location) extends MassChange[Location]

// Messages

@JsonCodec sealed trait ClientToServer
case class Ping(foo: String) extends ClientToServer

object ClientToServer

object FleetEvent

@JsonCodec sealed trait ServerToClient
case class FleetUpdate(state: FleetState, events: List[FleetEvent]) extends ServerToClient

object ServerToClient
