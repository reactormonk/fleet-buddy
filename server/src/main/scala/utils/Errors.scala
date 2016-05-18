package errors

sealed trait Err extends Throwable

case class StateNotFound(got: String, states: Set[String]) extends Err
case class ParseError(message: String) extends Err
case class ParseFailure(fail: org.http4s.ParseFailure) extends Err
case class ThrownException(exception: Throwable) extends Err {
  override def getCause = exception
}
