package utils
import shared._

import argonaut._, argonaut.Argonaut._, argonaut.ArgonautShapeless._
import shapeless._
import eveapi.utils.Decoders._
import eveapi.data.crest._
import org.http4s.Uri

object codecs {
  implicit val fse = implicitly[EncodeJson[FleetState]]
  implicit val fsd = implicitly[DecodeJson[FleetState]]
  implicit val fd = implicitly[DecodeJson[Fleet[Uri]]]
  implicit val fe = implicitly[EncodeJson[Fleet[Uri]]]
  implicit val pme = implicitly[EncodeJson[Paginated[Member[Uri]]]]
  implicit val pmd = implicitly[DecodeJson[Paginated[Member[Uri]]]]
  implicit val pwe = implicitly[EncodeJson[Paginated[Wing[Uri]]]]
  implicit val pwd = implicitly[DecodeJson[Paginated[Wing[Uri]]]]
}
