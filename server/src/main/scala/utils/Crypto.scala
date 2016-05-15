package utils

// Mostly borrowed from  https://github.com/playframework/playframework/blob/a5decc4ec81d311a769208ce007771a1e45b570b/framework/src/play/src/main/scala/play/api/libs/crypto/Crypto.scala
// but with less Inject

import org.atnos.eff._, org.atnos.eff.syntax.eff._, org.atnos.eff.syntax.all._, org.atnos.eff.all._
import scalaz._, Scalaz._
import java.time._

import javax.crypto.{ Cipher, Mac }
import javax.crypto.spec.{ IvParameterSpec, SecretKeySpec }
import org.apache.commons.codec.binary.Hex

object Crypto {
  def sign[R](message: String)(implicit k: Reader[PrivateKey, ?] <= R): Eff[R, String] = {
    for {
      key <- ask[R, PrivateKey]
    } yield {
      val mac = Mac.getInstance("HmacSHA1")
      mac.init(new SecretKeySpec(key.key, "HmacSHA1"))
      Hex.encodeHexString(mac.doFinal(message.getBytes("utf-8")))
    }
  }

  def signToken[R](token: String)(implicit c: Reader[Clock, ?] <= R, k: Reader[PrivateKey, ?] <= R): Eff[R, String] = {
    ask[R, Clock].flatMap({ clock =>
      val nonce = clock.millis()
      val joined = nonce + "-" + token
      sign(joined).map(_ + "-" + joined)
    })
  }

  def validateSignedToken[R](token: String)(implicit k: Reader[PrivateKey, ?] <= R): Eff[R, Option[String]] = {
    token.split("-", 3) match {
      case Array(signature, nonce, raw) => {
        sign(nonce + "-" + raw).map(signed => constantTimeEquals(signature, signed)).map({ ok =>
          if(ok) Some(raw) else None
        })
      }
      case _ => EffMonad.point(None)
    }
  }

  def constantTimeEquals(a: String, b: String): Boolean = {
    if (a.length != b.length) {
      false
    } else {
      var equal = 0
      for (i <- 0 until a.length) {
        equal |= a(i) ^ b(i)
      }
      equal == 0
    }
  }

}

case class PrivateKey(key: Array[Byte])
