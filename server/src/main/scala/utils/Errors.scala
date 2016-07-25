package errors

import eveapi.errors.EveApiError

sealed trait Err extends Throwable

case class ApiError(error: EveApiError) extends Err {
  override def getMessage = error.getMessage
}
