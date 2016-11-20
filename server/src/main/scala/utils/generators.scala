package utils

import org.scalacheck._
import org.http4s._
import java.time.Instant
import eveapi.compress._
import scala.collection.JavaConverters._
import models.StaticData._
import doobie.imports._
import shared.FleetState

case class FleetGen(ships: List[CompressedShip], solarSystems: List[CompressedSolarSystem]) {
  implicit val arbShip = Arbitrary(Gen.oneOf(ships))
  implicit val arbSS = Arbitrary(Gen.oneOf(solarSystems))

  val genCharacter: Gen[CompressedCharacter] =
    for {
      id <- Gen.choose(90000001, 97999999)
      name <- normalDist(30).flatMap({n => Gen.listOfN(n, Gen.alphaNumChar).map(_.mkString)})
    } yield {
      CompressedStandardIdentifier(
        id = id.toLong,
        name = name
      )
    }

  implicit val arbCharacter = Arbitrary(genCharacter)

  def compressedMember(ship: CompressedShip, system: CompressedSolarSystem, squadId: Long, station: Option[CompressedStation], wingId: Long): Gen[CompressedMember] =
    for {
      character <- Arbitrary.arbitrary[CompressedCharacter]
      joinTime <- Gen.choose(1000*30, 1000*60*60*8).map(mil => Instant.now.minusMillis(mil.toLong))
      takesFleetWarp <- Arbitrary.arbitrary[Boolean]
    } yield {
      CompressedMember(
        fleetID = 0,
        boosterID = 0,
        character = character,
        joinTime = joinTime,
        roleID = 0,
        ship = ship,
        solarSystem = system,
        squadID = squadId,
        station = station,
        wingID = wingId,
        takesFleetWarp = takesFleetWarp
      )
    }

  //... I hope
  def normalDist(upper: Int) = Gen.oneOf(Range(0, upper).toList)
  // Not tailrecursive, but small enough not to hit the stack.
  def distribution(size: Int): Gen[List[Int]] =
    normalDist(size).flatMap({ chop =>
      (size, chop) match {
        case (0, _) => List(chop)
        case (_, 0) => List(size)
        case _ => distribution(size - chop).map({inner => chop :: inner})
      }
    })

  // TODO generate station too
  val memberList: Gen[List[CompressedMember]] = {
    for {
      fleetSize <- normalDist(250)
      shipDist <- distribution(fleetSize)
      availableShips <- Gen.containerOfN[Vector, CompressedShip](shipDist.length, Arbitrary.arbitrary[CompressedShip])
      shipList = shipDist.zip(availableShips).flatMap({case (n, ship) => List.fill(n)(ship)})
      systemDist <- distribution(fleetSize)
      availableSystems <- Gen.containerOfN[Vector, CompressedSolarSystem](systemDist.length, Arbitrary.arbitrary[CompressedSolarSystem])
      systemList = systemDist.zip(availableSystems).flatMap({case (n, system) => List.fill(n)(system)})
      res <- Gen.sequence(Range(0, fleetSize).map({ i =>
        compressedMember(shipList(i), systemList(i), 0L, None, 0L)
      }))
    } yield {
      res.asScala.toList
    }
  }

  // TODO squad/wings
  val wingList: Gen[List[CompressedWing]] = Gen.const(List())

  def state: Gen[FleetState] = {
    for {
      wings <- wingList
      members <- memberList
    } yield {
      FleetState(CompressedFleet(0, false, false, false, "Sample motd."), members, wings, Instant.now)
    }
  }

  implicit val stateArb = Arbitrary(state)
}

object FleetGen {
  def apply(): ConnectionIO[FleetGen] = for {
    ships <- allShips.list
    systems <- allSystems.list
  } yield { FleetGen(ships, systems) }
}
