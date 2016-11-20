package utils
import java.time.Instant

object InstantVar {
  def unapply(str: String): Option[Instant] = {
    util.Try(Instant.parse(str)).toOption
  }
}
