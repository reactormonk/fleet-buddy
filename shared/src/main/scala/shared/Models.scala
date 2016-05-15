package eveapi

import java.time._

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import org.http4s._

trait Fetcher[T] {
  type Monad[_]
  def apply(l: Link[T])(implicit ev: Decoder[T]): Monad[T]
}

case class Link[T](href: Uri) {
  def apply()(implicit ev: Decoder[T], f: Fetcher[T]) = f(this)
}

// Even solar systems (8k in count) aren't paginated. Not implemeting for now.
case class Paginated[T](items: List[T], pageCount: Long, pageCount_str: String, totalCount: Long, totalCount_str: String)

case class Id[T](href: Uri, id: Long, id_str: String, name: String) {
  def link = Link[T](href)
}

/*
 * A Href pointing to a Uri you can POST to, but doesn't allow for GET.
 */
case class Href(href: Uri)

case class Fleet(
  isFreeMove: Boolean,
  isRegistered: Boolean,
  isVoiceEnabled: Boolean,
  members: Link[Paginated[Member]],
  motd: String,
  wings: Link[Paginated[Wing]]
)

case class Member(
  boosterID: Int,
  boosterID_str: String,
  boosterName: String,
  character: Character,
  href: Link[Member],
  joinTime: Instant,
  roleID: Int,
  roleID_str: String,
  roleName: String,
  ship: Id[Ship],
  solarSystem: Id[SolarSystem],
  squadID: Long,
  squadID_str: String,
  station: Id[Station],
  takesFleetWarp: Boolean,
  wingID: Long,
  wingID_str: String)

case class Wing(
  id: Long,
  id_str: String,
  name: String,
  squads: Href,
  squadsList: List[Squad]
)

case class Squad(
  id: Long,
  id_str: String,
  name: String
)

case class Character(
  // capsuleer: Link[Capsuleer], // Not enabled yet
)

case class SolarSystem()

case class Station()

case class Ship()

case class Capsuleer()
