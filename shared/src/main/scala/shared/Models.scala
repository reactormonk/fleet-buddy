package eveapi

import java.time._

import io.circe._, io.circe.generic._, io.circe.parser._, io.circe.syntax._, io.circe.java8.time._

trait Fetcher[T] {
  type Monad[_]
  def apply(l: Link[T])(implicit ev: Decoder[T]): Monad[T]
}

@JsonCodec case class Link[T](href: String) {
  def apply()(implicit ev: Decoder[T], f: Fetcher[T]) = f(this)
}

// Even solar systems (8k in count) aren't paginated. Not implemeting for now.
@JsonCodec case class Paginated[T](items: List[T], pageCount: Long, pageCount_str: String, totalCount: Long, totalCount_str: String)

@JsonCodec case class Id[T](href: String, id: Long, id_str: String, name: String) {
  def link = Link[T](href)
}

/*
 * A Href pointing to a Uri you can POST to, but doesn't allow for GET.
 */
@JsonCodec case class Href(href: String)

@JsonCodec case class Fleet(
  isFreeMove: Boolean,
  isRegistered: Boolean,
  isVoiceEnabled: Boolean,
  members: Link[Paginated[Member]],
  motd: String,
  wings: Link[Paginated[Wing]]
)

@JsonCodec case class Member(
  boosterID: Int,
  boosterID_str: String,
  boosterName: String,
  character: Character,
  href: String, // TODO
  joinTime: String,
  roleID: Int,
  roleID_str: String,
  roleName: String,
  ship: Id[Ship],
  solarSystem: Id[SolarSystem],
  squadID: Long,
  squadID_str: String,
  station: Option[Id[Station]],
  takesFleetWarp: Boolean,
  wingID: Long,
  wingID_str: String)

@JsonCodec case class Wing(
  id: Long,
  id_str: String,
  name: String,
  squads: Href,
  squadsList: List[Squad]
)

@JsonCodec case class Squad(
  id: Long,
  id_str: String,
  name: String
)

@JsonCodec case class Character(
  // capsuleer: Link[Capsuleer], // Not enabled yet
)

@JsonCodec case class SolarSystem()

@JsonCodec case class Station()

@JsonCodec case class Ship()

@JsonCodec case class Capsuleer()

// Messages

@JsonCodec sealed trait ServerToClient
case class FleetState(fleet: Fleet, members: List[Member], wings: List[Wing]) extends ServerToClient

object ServerToClient

@JsonCodec sealed trait ClientToServer
case class Ping(foo: String) extends ClientToServer

object ClientToServer
