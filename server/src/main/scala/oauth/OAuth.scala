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
import org.reactormonk._

import effects._
import errors._
import utils._

case class OAuthAuth(key: PrivateKey, clock: Clock) {
  val crypto = CryptoBits(key)
  def setCookie(message: String): Cookie = {
    val signed = crypto.signToken(message, clock.millis.toString)
    Cookie("authcookie", signed)
  }

  def verifyCookie(cookies: headers.Cookie): Option[String] = {
    cookies.values.list.find(_.name == "authcookie")
      .flatMap({c => crypto.validateSignedToken(c.content) })
  }

  def maybeAuth(request: Request): Option[String] = {
    headers.Cookie.from(request.headers).flatMap(verifyCookie)
  }
}
