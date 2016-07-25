package utils

import scala.collection.mutable.Buffer
import shared._
import eveapi.data.crest._
import eveapi.compress._

object FleetDiff {
  def keyBy[K, V](coll: Iterable[V], f: V => K): Map[K, V] = coll.map(f).zip(coll).toMap

  def apply(old: FleetState, now: FleetState): List[FleetEvent] = {
    val newMembers = keyBy[CompressedCharacter, CompressedMember](now.members, _.character)
    val oldMembers = keyBy[CompressedCharacter, CompressedMember](old.members, _.character)
    val zippedMembers: List[(CompressedMember, CompressedMember)] = old.members.flatMap(member => newMembers.get(member.character).map((member, _)))
    val memberChanges = member(
      keyBy[Long, CompressedSquad](old.wings.flatMap(_.squadsList), _.squadId), keyBy[Long, CompressedWing](old.wings, _.wingId),
      keyBy[Long, CompressedSquad](now.wings.flatMap(_.squadsList), _.squadId), keyBy[Long, CompressedWing](now.wings, _.wingId)
    ) _
    fleet(old.fleet, now.fleet) ++
      joinsparts(oldMembers, newMembers) ++
      locations(zippedMembers.flatMap((location _).tupled(_).toList)) ++
      zippedMembers.flatMap(memberChanges.tupled)
  }
  def joinsparts(old: Map[CompressedCharacter, CompressedMember], now: Map[CompressedCharacter, CompressedMember]): List[FleetEvent] = {
    val parts = (old.keySet -- now.keySet).map(id => MemberPart(id, old(id)))
    val joins = (now.keySet -- old.keySet).map(id => MemberJoin(id, now(id)))
    (parts ++ joins).toList
  }
  def locations(changes: List[LocationChange]): List[Change[CompressedLocation]] = {
    changes
      .groupBy(lc => (lc.old, lc.now))
      .map({ case ((old, now), changes) =>
        if(changes.size > 1) {
          MassLocationChange(changes.map(_.id), old, now)
        } else { changes.head }
      }).toList
  }
  def location(old: CompressedMember, now: CompressedMember): Option[LocationChange] = {
    val oldLoc = CompressedLocation(Some(old.solarSystem), old.station)
    val newLoc = CompressedLocation(Some(now.solarSystem), now.station)
    if(oldLoc != newLoc) {
      Some(LocationChange(old.character, oldLoc, newLoc))
    } else {None}
  }
  def member(
    oldSquads: Map[Long, CompressedSquad], oldWings: Map[Long, CompressedWing],
    newSquads: Map[Long, CompressedSquad], newWings: Map[Long, CompressedWing]
  )
    (old: CompressedMember, now: CompressedMember): List[Individual] = {
    val res = Buffer[Individual]()
    if(old.squadID != now.squadID)
      res += SquadMove(old.character, oldSquads(old.squadID), newSquads(now.squadID))
    if(old.wingID != now.wingID)
      res += WingMove(old.character, oldWings(old.wingID), newWings(now.wingID))
    if(old.ship != now.ship)
      res += ShipChange(old.character, old.ship, now.ship, CompressedLocation(Some(now.solarSystem), now.station))
    if(old.takesFleetWarp != now.takesFleetWarp)
      res += FleetWarpChange(old.character, old.takesFleetWarp, now.takesFleetWarp)
    res.toList
  }
  def fleet(old: CompressedFleet, now: CompressedFleet): List[FleetEvent] = {
    val res = Buffer[FleetEvent]()
    if(old.isFreeMove != now.isFreeMove)
      res += FreeMoveChange(old.isFreeMove, now.isFreeMove)
    if(old.isRegistered != now.isRegistered)
      res += RegisteredChange(old.isRegistered, now.isRegistered)
    if(old.motd != now.motd)
      res += MotdChange(old.motd, now.motd)
    res.toList
  }
}
