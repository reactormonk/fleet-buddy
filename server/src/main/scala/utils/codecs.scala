package utils
import shared._

import argonaut._, argonaut.Argonaut._, argonaut.ArgonautShapeless._
import shapeless._
import eveapi.utils.Decoders._
import eveapi.data.crest._
import org.http4s.Uri
import scalaz._, Scalaz._

object codecs {
  // javascript numbers are max. 53 bit, Longs are longer.
  implicit val longToString: CodecJson[Long] = CodecJson(
    (l: Long) => Json.jString(l.toString),
    c => c.as[String].flatMap(str =>
      \/.fromTryCatchNonFatal(str.toLong).fold(err => err match {
        case e: NumberFormatException => DecodeResult.fail(e.toString, c.history)
        case e => throw e
      },
        DecodeResult.ok
      )
    )
  )

  implicit val fse = implicitly[EncodeJson[FleetState]]
  implicit val fsd = implicitly[DecodeJson[FleetState]]
  implicit val fd = implicitly[DecodeJson[Fleet[Uri]]]
  implicit val fe = implicitly[EncodeJson[Fleet[Uri]]]
  implicit val pme = implicitly[EncodeJson[Paginated[Member[Uri]]]]
  implicit val pmd = implicitly[DecodeJson[Paginated[Member[Uri]]]]
  implicit val pwe = implicitly[EncodeJson[Paginated[Wing[Uri]]]]
  implicit val pwd = implicitly[DecodeJson[Paginated[Wing[Uri]]]]
  implicit val fue = implicitly[DecodeJson[FleetUpdates]]
  implicit val fud = implicitly[EncodeJson[FleetUpdates]]
}
