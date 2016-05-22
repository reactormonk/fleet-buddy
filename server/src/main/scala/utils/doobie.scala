package utils

import java.time._
import doobie.imports._

object Doobie {
  implicit val instantMeta: Meta[Instant] =
    Meta[java.sql.Timestamp].xmap[Instant](
      timestamp => timestamp.toInstant,
      instant => java.sql.Timestamp.from(instant)
    )
}
