package errors

import io.circe._
import org.http4s._, org.http4s.dsl._, org.http4s.client._, org.http4s.circe._

sealed trait Err extends Throwable

case class StateNotFound(got: String, states: Set[String]) extends Err {
  override def getMessage = s"Got $got, expected $states"
}
case class ParseError(message: String) extends Err {
  override def getMessage = message
}
case class JsonParseError(message: Error) extends Err {
  override def getMessage = message.toString
}
case class ParseFailure(fail: org.http4s.ParseFailure) extends Err {
  override def getMessage = fail.toString
}
case class EveApiStatusFailed(fail: Status, body: String) extends Err {
  override def getMessage = s"Failed with: $fail Message: $body"
}
case class ThrownException(exception: Throwable) extends Err {
  override def getCause = exception
}
