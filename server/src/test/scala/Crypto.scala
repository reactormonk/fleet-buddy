package utils

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import java.time._

import scalaz._, Scalaz._
import org.atnos.eff._, org.atnos.eff.syntax.all._, org.atnos.eff.all._

object CryptSpecification extends Properties("Crypto") {
  val clock = Clock.systemUTC()
  val key = PrivateKey(scala.io.Codec.toUTF8("OhPh0AengeeshochooYu"))
  property("sign & validate") = forAll { message: String =>
    val eff: Eff[Reader[PrivateKey, ?] |: Reader[Clock, ?] |: NoEffect, Boolean] = for {
      signed <- Crypto.signToken[Reader[PrivateKey, ?] |: Reader[Clock, ?] |: NoEffect](message)
      validated <- Crypto.validateSignedToken[Reader[PrivateKey, ?] |: Reader[Clock, ?] |: NoEffect](signed)
    } yield validated.isDefined
    eff.runReader(key).runReader(clock).run
  }
}
