package utils

import cats.{ MonadError, SemigroupK }
import cats.data.{ Kleisli, NonEmptyList, OneAnd, Validated, Xor }
import cats.std.list._
import cats.syntax.functor._
import io.circe._
import org.http4s._
import Decoder._

object Decoders {
  implicit final val decodeUri: Decoder[Uri] = Decoder[String].flatMap({ string =>
    new Decoder[Uri] {
      final def apply(c: HCursor): Result[Uri] =
        Uri.fromString(string).fold(err => Xor.left(DecodingFailure(err.toString, c.history)), Xor.right)
    }
  })
  implicit final val encodeUri: Encoder[Uri] = Encoder.instance(uri => Json.fromString(uri.renderString))
}
