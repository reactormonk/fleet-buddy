package oauth

import org.atnos.eff._, org.atnos.eff.syntax.all._, org.atnos.eff.all._
import scalaz._, Scalaz._
import scalaz.concurrent._
import scala.util.Random
import scala.io.Codec
import org.apache.commons.codec.binary.Base64
import org.http4s._, org.http4s.dsl._, org.http4s.client._, org.http4s.argonaut._, org.http4s.util.{CaseInsensitiveString => CIS}
import java.time._
import java.time.temporal.ChronoUnit._

import effects._
import errors._
import utils._

case class OAuthAuth(key: PrivateKey, clock: Clock) {
  def setCookie(message: String): Cookie = {
    val signed = Crypto.signToken[Fx.fx2[Reader[PrivateKey, ?], Reader[Clock, ?]]](message).runReader(key).runReader(clock).run
    Cookie("authcookie", signed)
  }

  def verifyCookie[R](cookies: headers.Cookie)(implicit key: Reader[PrivateKey, ?] <= R, clock: Reader[Clock, ?] <= R): Eff[R, Option[String]] = {
    cookies.values.list.find(_.name == "authcookie")
      .map({c => Crypto.validateSignedToken[R](c.content) })
      .sequence.map(_.flatten)
  }

  def maybeAuth(request: Request): Option[String] = {
    headers.Cookie.from(request.headers).flatMap(c =>
      verifyCookie[Fx.fx2[Reader[PrivateKey, ?], Reader[Clock, ?]]](c).runReader(key).runReader(clock).run
    )
  }
}
