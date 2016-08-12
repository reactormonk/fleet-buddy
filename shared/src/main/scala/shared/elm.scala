import eveapi.data.crest._
import eveapi.compress._
import java.time._
import elmtype._
import elmtype.derive._
import ElmTypeShapeless._
import shapeless._
import shared._

object Main {
  implicit val elmlong = RawType[Long]("String", "Encode.string", "Decode.string")

  object ElmTypes extends ElmTypeMain(ToElmTypes[ClientToServer :: ServerToClient :: HNil].apply)
}
