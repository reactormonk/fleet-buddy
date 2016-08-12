package shared

import eveapi.data.crest._
import eveapi.compress._
import java.time._
import elmtype._
import elmtype.derive._
import ElmTypeShapeless._
import shapeless._

trait Id[T] {
  def id: Long
}

case class FleetState(fleet: CompressedFleet, members: List[CompressedMember], wings: List[CompressedWing])

sealed trait FleetEvent
sealed trait Change[T] extends FleetEvent {
  def old: T
  def now: T
}
sealed trait Individual extends FleetEvent {
  def id: CompressedCharacter
}
sealed trait MassChange[T] extends Change[T] {
  def ids: List[CompressedCharacter]
}

case class MotdChange(old: String, now: String) extends Change[String]
case class FreeMoveChange(old: Boolean, now: Boolean) extends Change[Boolean]
case class RegisteredChange(old: Boolean, now: Boolean) extends Change[Boolean]

case class SquadMove(id: CompressedCharacter, old: CompressedSquad, now: CompressedSquad) extends Change[CompressedSquad] with Individual
case class WingMove(id: CompressedCharacter, old: CompressedWing, now: CompressedWing) extends Change[CompressedWing] with Individual
case class ShipChange(id: CompressedCharacter, old: CompressedShip, now: CompressedShip, where: CompressedLocation) extends Change[CompressedShip] with Individual
case class LocationChange(id: CompressedCharacter, old: CompressedLocation, now: CompressedLocation) extends Change[CompressedLocation] with Individual
case class FleetWarpChange(id: CompressedCharacter, old: Boolean, now: Boolean) extends Change[Boolean] with Individual

case class MemberJoin(id: CompressedCharacter, now: CompressedMember) extends FleetEvent
case class MemberPart(id: CompressedCharacter, old: CompressedMember) extends FleetEvent

case class MassLocationChange(ids: List[CompressedCharacter], old: CompressedLocation, now: CompressedLocation) extends MassChange[CompressedLocation]

// Messages

sealed trait ClientToServer
case class Ping(foo: String) extends ClientToServer

sealed trait ServerToClient
case class FleetUpdates(state: FleetState, events: List[FleetEvent]) extends ServerToClient
