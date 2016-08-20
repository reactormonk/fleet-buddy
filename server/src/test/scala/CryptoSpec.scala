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
    val eff: Eff[Fx.fx2[Reader[PrivateKey, ?], Reader[Clock, ?]], Boolean] = for {
      signed <- Crypto.signToken[Fx.fx2[Reader[PrivateKey, ?], Reader[Clock, ?]]](message)
      validated <- Crypto.validateSignedToken[Fx.fx2[Reader[PrivateKey, ?], Reader[Clock, ?]]](signed)
    } yield validated.isDefined
    eff.runReader(key).runReader(clock).run
  }
}
