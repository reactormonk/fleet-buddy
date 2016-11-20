package models

import doobie.imports._
import eveapi.compress._
import shapeless._
import eveapi.data.crest._

object StaticData {
  val allShips: Query0[CompressedShip] = sql"""
select "typeID", "typeName" from "invTypes" t
 join "invGroups" g on (t."groupID" = g."groupID")
where t.published = true
and g."categoryID" = 6;
""".query[Int :: String :: HNil].map({ case i :: s :: HNil => CompressedStandardIdentifier[Ship](i.toLong, s)})

  val allSystems: Query0[CompressedSolarSystem] = sql"""
select "solarSystemID", "solarSystemName" from "mapSolarSystems"
""".query[Int :: String :: HNil].map({ case i :: s :: HNil => CompressedStandardIdentifier[SolarSystem](i.toLong, s)})

  val allStations: Query0[(CompressedSolarSystem, CompressedStation)] = sql"""
select s."solarSystemID", "solarSystemName", "stationID", "stationName" from "staStations" s
join "mapSolarSystems" ss on (ss."solarSystemID" = s."solarSystemID")
""".query[Int :: String :: Int :: String :: HNil].map({ case ssi :: ssn :: si :: sn :: HNil => (CompressedStandardIdentifier[SolarSystem](ssi.toLong, ssn), CompressedStandardIdentifier[Station](si.toLong, sn))})

}
