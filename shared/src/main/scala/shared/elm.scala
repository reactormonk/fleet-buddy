import eveapi.data.crest._
import eveapi.compress._
import java.time._
import elmtype._
import elmtype.derive._
import ElmTypeShapeless._
import shapeless._
import shared._

object Elm {
  implicit val elmlong = RawType[Long]("String", "Encode.string", "Decode.string")
  val types = ToElmTypes[ClientToServer :: ServerToClient :: HNil].apply
}

object ElmTypes extends ElmTypeMain(Elm.types)
