package eveapi

import scala.collection.mutable.Buffer

object FleetDiff {
  def keyBy[K, V](coll: Iterable[V], f: V => K): Map[K, V] = coll.map(f).zip(coll).toMap

  def apply(old: FleetState, now: FleetState): List[FleetEvent] = {
    val newMembers = keyBy[Id[Character], Member](now.members, _.character)
    val oldMembers = keyBy[Id[Character], Member](old.members, _.character)
    val zippedMembers: List[(Member, Member)] = old.members.flatMap(member => newMembers.get(member.character).map((member, _)))
    val memberChanges = member(
      keyBy[Long, Squad](old.wings.flatMap(_.squadsList), _.id), keyBy[Long, Wing](old.wings, _.id),
      keyBy[Long, Squad](now.wings.flatMap(_.squadsList), _.id), keyBy[Long, Wing](now.wings, _.id)
    ) _
    fleet(old.fleet, now.fleet) ++
      joinsparts(oldMembers, newMembers) ++
      locations(zippedMembers.flatMap((location _).tupled(_).toList)) ++
      zippedMembers.flatMap(memberChanges.tupled)
  }
  def joinsparts(old: Map[Id[Character], Member], now: Map[Id[Character], Member]): List[FleetEvent] = {
    val parts = (old.keySet -- now.keySet).map(id => MemberPart(id, old(id)))
    val joins = (now.keySet -- old.keySet).map(id => MemberJoin(id, now(id)))
    (parts ++ joins).toList
  }
  def locations(changes: List[LocationChange]): List[Change[Location]] = {
    changes
      .groupBy(lc => (lc.old, lc.now))
      .map({ case ((old, now), changes) =>
        if(changes.size > 1) {
          MassLocationChange(changes.map(_.id), old, now)
        } else { changes.head }
      }).toList
  }
  def location(old: Member, now: Member): Option[LocationChange] = {
    val oldLoc = Location(old)
    val newLoc = Location(now)
    if(oldLoc != newLoc) {
      Some(LocationChange(old.character, oldLoc, newLoc))
    } else {None}
  }
  def member(
    oldSquads: Map[Long, Squad], oldWings: Map[Long, Wing],
    newSquads: Map[Long, Squad], newWings: Map[Long, Wing]
  )
    (old: Member, now: Member): List[Individual] = {
    val res = Buffer[Individual]()
    if(old.squadID != now.squadID)
      res += SquadMove(old.character, oldSquads(old.squadID), newSquads(now.squadID))
    if(old.wingID != now.wingID)
      res += WingMove(old.character, oldWings(old.wingID), newWings(now.wingID))
    if(old.ship != now.ship)
      res += ShipChange(old.character, old.ship, now.ship, Location(now))
    if(old.takesFleetWarp != now.takesFleetWarp)
      res += FleetWarpChange(old.character, old.takesFleetWarp, now.takesFleetWarp)
    res.toList
  }
  def fleet(old: Fleet, now: Fleet): List[FleetEvent] = {
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
