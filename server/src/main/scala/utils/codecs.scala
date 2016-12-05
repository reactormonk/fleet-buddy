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

  implicit val fse: EncodeJson[FleetState] = cachedImplicit
  implicit val fsd: DecodeJson[FleetState] = cachedImplicit
  implicit val fd: DecodeJson[Fleet[Uri]] = cachedImplicit
  implicit val fe: EncodeJson[Fleet[Uri]] = cachedImplicit
  implicit val pme: EncodeJson[Paginated[Member[Uri]]] = cachedImplicit
  implicit val pmd: DecodeJson[Paginated[Member[Uri]]] = cachedImplicit
  implicit val pwe: EncodeJson[Paginated[Wing[Uri]]] = cachedImplicit
  implicit val pwd: DecodeJson[Paginated[Wing[Uri]]] = cachedImplicit
  implicit val fue: DecodeJson[FleetUpdates] = cachedImplicit
  implicit val fud: EncodeJson[FleetUpdates] = cachedImplicit
}
