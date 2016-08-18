module Codec exposing (..)
import Date exposing (Date)
import Json.Decode.Extra exposing(..)
import Json.Decode as Decode exposing ( (:=) )
import Json.Encode as Encode
import Date.Extra exposing (toUtcIsoString)
type ClientToServer = ClientToServerPing Ping
type alias Ping = { foo : String }
decodeClientToServer : Decode.Decoder ClientToServer
decodeClientToServer = Decode.oneOf
  [ ("Ping" := Decode.map ClientToServerPing decodePing)
  ]
decodePing : Decode.Decoder Ping
decodePing =
  Decode.succeed Ping |: ("foo" := Decode.string)

encodeClientToServer: ClientToServer -> Encode.Value
encodeClientToServer obj =
  let
    (typefield, inner) = case obj of
      ClientToServerPing obj2 -> ("Ping", encodePing obj2)
    in
      Encode.object [(typefield, inner)]
encodePing : Ping -> Encode.Value
encodePing obj = Encode.object
  [ ("foo", Encode.string obj.foo)
  ]
type ServerToClient = ServerToClientFleetUpdates FleetUpdates | ServerToClientServerError ServerError
type alias FleetUpdates = { state : FleetState, events : List FleetEvent }
type alias FleetState = { fleet : CompressedFleet, members : List CompressedMember, wings : List CompressedWing, now : Date }
type alias CompressedFleet = { fleetId : String, isFreeMove : Bool, isRegistered : Bool, isVoiceEnabled : Bool, motd : String }
type alias CompressedMember = { fleetId : String, boosterID : Int, character : CompressedStandardIdentifier_Long_String, joinTime : Date, roleID : Int, ship : CompressedStandardIdentifier_Long_String, solarSystem : CompressedStandardIdentifier_Long_String, squadID : String, station : Maybe CompressedStandardIdentifier_Long_String, takesFleetWarp : Bool, wingID : String }
type alias CompressedStandardIdentifier_Long_String = { id : String, name : String }
type alias CompressedWing = { fleetId : String, wingId : String, name : String, squadsList : List CompressedSquad }
type alias CompressedSquad = { fleetId : String, wingId : String, squadId : String, name : String }
type FleetEvent = FleetEventFleetWarpChange FleetWarpChange | FleetEventFreeMoveChange FreeMoveChange | FleetEventLocationChange LocationChange | FleetEventMassLocationChange MassLocationChange | FleetEventMemberJoin MemberJoin | FleetEventMemberPart MemberPart | FleetEventMotdChange MotdChange | FleetEventRegisteredChange RegisteredChange | FleetEventShipChange ShipChange | FleetEventSquadMove SquadMove | FleetEventWingMove WingMove
type alias FleetWarpChange = { id : CompressedStandardIdentifier_Long_String, old : Bool, now : Bool }
type alias FreeMoveChange = { old : Bool, now : Bool }
type alias LocationChange = { id : CompressedStandardIdentifier_Long_String, old : CompressedLocation, now : CompressedLocation }
type alias CompressedLocation = { solarSystem : Maybe CompressedStandardIdentifier_Long_String, station : Maybe CompressedStandardIdentifier_Long_String }
type alias MassLocationChange = { ids : List CompressedStandardIdentifier_Long_String, old : CompressedLocation, now : CompressedLocation }
type alias MemberJoin = { id : CompressedStandardIdentifier_Long_String, now : CompressedMember }
type alias MemberPart = { id : CompressedStandardIdentifier_Long_String, old : CompressedMember }
type alias MotdChange = { old : String, now : String }
type alias RegisteredChange = { old : Bool, now : Bool }
type alias ShipChange = { id : CompressedStandardIdentifier_Long_String, old : CompressedStandardIdentifier_Long_String, now : CompressedStandardIdentifier_Long_String, where_escaped : CompressedLocation }
type alias SquadMove = { id : CompressedStandardIdentifier_Long_String, old : CompressedSquad, now : CompressedSquad }
type alias WingMove = { id : CompressedStandardIdentifier_Long_String, old : CompressedWing, now : CompressedWing }
type alias ServerError = { error : EveException }
type EveException = EveExceptionAccessDeniedForbiddenError AccessDeniedForbiddenError | EveExceptionForbiddenError ForbiddenError | EveExceptionUnauthorizedError UnauthorizedError | EveExceptionUnsupportedMediaTypeError UnsupportedMediaTypeError
type alias AccessDeniedForbiddenError = { key : String, isLocalized : Bool, message : String }
type alias ForbiddenError = { isLocalized : Bool, key : String, message : String, title : Maybe String }
type alias UnauthorizedError = { key : String, isLocalized : Bool, message : String }
type alias UnsupportedMediaTypeError = { key : String, message : String }
decodeServerToClient : Decode.Decoder ServerToClient
decodeServerToClient = Decode.oneOf
  [ ("FleetUpdates" := Decode.map ServerToClientFleetUpdates decodeFleetUpdates)
  , ("ServerError" := Decode.map ServerToClientServerError decodeServerError)
  ]
decodeFleetUpdates : Decode.Decoder FleetUpdates
decodeFleetUpdates =
  Decode.succeed FleetUpdates |: ("state" := decodeFleetState) |: ("events" := Decode.list decodeFleetEvent)
decodeFleetState : Decode.Decoder FleetState
decodeFleetState =
  Decode.succeed FleetState |: ("fleet" := decodeCompressedFleet) |: ("members" := Decode.list decodeCompressedMember) |: ("wings" := Decode.list decodeCompressedWing) |: ("now" := date)
decodeCompressedFleet : Decode.Decoder CompressedFleet
decodeCompressedFleet =
  Decode.succeed CompressedFleet |: ("fleetId" := Decode.string) |: ("isFreeMove" := Decode.bool) |: ("isRegistered" := Decode.bool) |: ("isVoiceEnabled" := Decode.bool) |: ("motd" := Decode.string)
decodeCompressedMember : Decode.Decoder CompressedMember
decodeCompressedMember =
  Decode.succeed CompressedMember |: ("fleetId" := Decode.string) |: ("boosterID" := Decode.int) |: ("character" := decodeCompressedStandardIdentifier_Long_String) |: ("joinTime" := date) |: ("roleID" := Decode.int) |: ("ship" := decodeCompressedStandardIdentifier_Long_String) |: ("solarSystem" := decodeCompressedStandardIdentifier_Long_String) |: ("squadID" := Decode.string) |: ("station" := Decode.maybe decodeCompressedStandardIdentifier_Long_String) |: ("takesFleetWarp" := Decode.bool) |: ("wingID" := Decode.string)
decodeCompressedStandardIdentifier_Long_String : Decode.Decoder CompressedStandardIdentifier_Long_String
decodeCompressedStandardIdentifier_Long_String =
  Decode.succeed CompressedStandardIdentifier_Long_String |: ("id" := Decode.string) |: ("name" := Decode.string)
decodeCompressedWing : Decode.Decoder CompressedWing
decodeCompressedWing =
  Decode.succeed CompressedWing |: ("fleetId" := Decode.string) |: ("wingId" := Decode.string) |: ("name" := Decode.string) |: ("squadsList" := Decode.list decodeCompressedSquad)
decodeCompressedSquad : Decode.Decoder CompressedSquad
decodeCompressedSquad =
  Decode.succeed CompressedSquad |: ("fleetId" := Decode.string) |: ("wingId" := Decode.string) |: ("squadId" := Decode.string) |: ("name" := Decode.string)
decodeFleetEvent : Decode.Decoder FleetEvent
decodeFleetEvent = Decode.oneOf
  [ ("FleetWarpChange" := Decode.map FleetEventFleetWarpChange decodeFleetWarpChange)
  , ("FreeMoveChange" := Decode.map FleetEventFreeMoveChange decodeFreeMoveChange)
  , ("LocationChange" := Decode.map FleetEventLocationChange decodeLocationChange)
  , ("MassLocationChange" := Decode.map FleetEventMassLocationChange decodeMassLocationChange)
  , ("MemberJoin" := Decode.map FleetEventMemberJoin decodeMemberJoin)
  , ("MemberPart" := Decode.map FleetEventMemberPart decodeMemberPart)
  , ("MotdChange" := Decode.map FleetEventMotdChange decodeMotdChange)
  , ("RegisteredChange" := Decode.map FleetEventRegisteredChange decodeRegisteredChange)
  , ("ShipChange" := Decode.map FleetEventShipChange decodeShipChange)
  , ("SquadMove" := Decode.map FleetEventSquadMove decodeSquadMove)
  , ("WingMove" := Decode.map FleetEventWingMove decodeWingMove)
  ]
decodeFleetWarpChange : Decode.Decoder FleetWarpChange
decodeFleetWarpChange =
  Decode.succeed FleetWarpChange |: ("id" := decodeCompressedStandardIdentifier_Long_String) |: ("old" := Decode.bool) |: ("now" := Decode.bool)
decodeFreeMoveChange : Decode.Decoder FreeMoveChange
decodeFreeMoveChange =
  Decode.succeed FreeMoveChange |: ("old" := Decode.bool) |: ("now" := Decode.bool)
decodeLocationChange : Decode.Decoder LocationChange
decodeLocationChange =
  Decode.succeed LocationChange |: ("id" := decodeCompressedStandardIdentifier_Long_String) |: ("old" := decodeCompressedLocation) |: ("now" := decodeCompressedLocation)
decodeCompressedLocation : Decode.Decoder CompressedLocation
decodeCompressedLocation =
  Decode.succeed CompressedLocation |: ("solarSystem" := Decode.maybe decodeCompressedStandardIdentifier_Long_String) |: ("station" := Decode.maybe decodeCompressedStandardIdentifier_Long_String)
decodeMassLocationChange : Decode.Decoder MassLocationChange
decodeMassLocationChange =
  Decode.succeed MassLocationChange |: ("ids" := Decode.list decodeCompressedStandardIdentifier_Long_String) |: ("old" := decodeCompressedLocation) |: ("now" := decodeCompressedLocation)
decodeMemberJoin : Decode.Decoder MemberJoin
decodeMemberJoin =
  Decode.succeed MemberJoin |: ("id" := decodeCompressedStandardIdentifier_Long_String) |: ("now" := decodeCompressedMember)
decodeMemberPart : Decode.Decoder MemberPart
decodeMemberPart =
  Decode.succeed MemberPart |: ("id" := decodeCompressedStandardIdentifier_Long_String) |: ("old" := decodeCompressedMember)
decodeMotdChange : Decode.Decoder MotdChange
decodeMotdChange =
  Decode.succeed MotdChange |: ("old" := Decode.string) |: ("now" := Decode.string)
decodeRegisteredChange : Decode.Decoder RegisteredChange
decodeRegisteredChange =
  Decode.succeed RegisteredChange |: ("old" := Decode.bool) |: ("now" := Decode.bool)
decodeShipChange : Decode.Decoder ShipChange
decodeShipChange =
  Decode.succeed ShipChange |: ("id" := decodeCompressedStandardIdentifier_Long_String) |: ("old" := decodeCompressedStandardIdentifier_Long_String) |: ("now" := decodeCompressedStandardIdentifier_Long_String) |: ("where" := decodeCompressedLocation)
decodeSquadMove : Decode.Decoder SquadMove
decodeSquadMove =
  Decode.succeed SquadMove |: ("id" := decodeCompressedStandardIdentifier_Long_String) |: ("old" := decodeCompressedSquad) |: ("now" := decodeCompressedSquad)
decodeWingMove : Decode.Decoder WingMove
decodeWingMove =
  Decode.succeed WingMove |: ("id" := decodeCompressedStandardIdentifier_Long_String) |: ("old" := decodeCompressedWing) |: ("now" := decodeCompressedWing)
decodeServerError : Decode.Decoder ServerError
decodeServerError =
  Decode.succeed ServerError |: ("error" := decodeEveException)
decodeEveException : Decode.Decoder EveException
decodeEveException = Decode.oneOf
  [ ("AccessDeniedForbiddenError" := Decode.map EveExceptionAccessDeniedForbiddenError decodeAccessDeniedForbiddenError)
  , ("ForbiddenError" := Decode.map EveExceptionForbiddenError decodeForbiddenError)
  , ("UnauthorizedError" := Decode.map EveExceptionUnauthorizedError decodeUnauthorizedError)
  , ("UnsupportedMediaTypeError" := Decode.map EveExceptionUnsupportedMediaTypeError decodeUnsupportedMediaTypeError)
  ]
decodeAccessDeniedForbiddenError : Decode.Decoder AccessDeniedForbiddenError
decodeAccessDeniedForbiddenError =
  Decode.succeed AccessDeniedForbiddenError |: ("key" := Decode.string) |: ("isLocalized" := Decode.bool) |: ("message" := Decode.string)
decodeForbiddenError : Decode.Decoder ForbiddenError
decodeForbiddenError =
  Decode.succeed ForbiddenError |: ("isLocalized" := Decode.bool) |: ("key" := Decode.string) |: ("message" := Decode.string) |: ("title" := Decode.maybe Decode.string)
decodeUnauthorizedError : Decode.Decoder UnauthorizedError
decodeUnauthorizedError =
  Decode.succeed UnauthorizedError |: ("key" := Decode.string) |: ("isLocalized" := Decode.bool) |: ("message" := Decode.string)
decodeUnsupportedMediaTypeError : Decode.Decoder UnsupportedMediaTypeError
decodeUnsupportedMediaTypeError =
  Decode.succeed UnsupportedMediaTypeError |: ("key" := Decode.string) |: ("message" := Decode.string)

encodeServerToClient: ServerToClient -> Encode.Value
encodeServerToClient obj =
  let
    (typefield, inner) = case obj of
      ServerToClientFleetUpdates obj2 -> ("FleetUpdates", encodeFleetUpdates obj2)
      ServerToClientServerError obj2 -> ("ServerError", encodeServerError obj2)
    in
      Encode.object [(typefield, inner)]
encodeFleetUpdates : FleetUpdates -> Encode.Value
encodeFleetUpdates obj = Encode.object
  [ ("state", encodeFleetState obj.state)
  , ("events", Encode.list <| List.map encodeFleetEvent obj.events)
  ]
encodeFleetState : FleetState -> Encode.Value
encodeFleetState obj = Encode.object
  [ ("fleet", encodeCompressedFleet obj.fleet)
  , ("members", Encode.list <| List.map encodeCompressedMember obj.members)
  , ("wings", Encode.list <| List.map encodeCompressedWing obj.wings)
  , ("now", Encode.string <| toUtcIsoString obj.now)
  ]
encodeCompressedFleet : CompressedFleet -> Encode.Value
encodeCompressedFleet obj = Encode.object
  [ ("fleetId", Encode.string obj.fleetId)
  , ("isFreeMove", Encode.bool obj.isFreeMove)
  , ("isRegistered", Encode.bool obj.isRegistered)
  , ("isVoiceEnabled", Encode.bool obj.isVoiceEnabled)
  , ("motd", Encode.string obj.motd)
  ]
encodeCompressedMember : CompressedMember -> Encode.Value
encodeCompressedMember obj = Encode.object
  [ ("fleetId", Encode.string obj.fleetId)
  , ("boosterID", Encode.int obj.boosterID)
  , ("character", encodeCompressedStandardIdentifier_Long_String obj.character)
  , ("joinTime", Encode.string <| toUtcIsoString obj.joinTime)
  , ("roleID", Encode.int obj.roleID)
  , ("ship", encodeCompressedStandardIdentifier_Long_String obj.ship)
  , ("solarSystem", encodeCompressedStandardIdentifier_Long_String obj.solarSystem)
  , ("squadID", Encode.string obj.squadID)
  , ("station", Maybe.withDefault Encode.null <| Maybe.map encodeCompressedStandardIdentifier_Long_String obj.station)
  , ("takesFleetWarp", Encode.bool obj.takesFleetWarp)
  , ("wingID", Encode.string obj.wingID)
  ]
encodeCompressedStandardIdentifier_Long_String : CompressedStandardIdentifier_Long_String -> Encode.Value
encodeCompressedStandardIdentifier_Long_String obj = Encode.object
  [ ("id", Encode.string obj.id)
  , ("name", Encode.string obj.name)
  ]
encodeCompressedWing : CompressedWing -> Encode.Value
encodeCompressedWing obj = Encode.object
  [ ("fleetId", Encode.string obj.fleetId)
  , ("wingId", Encode.string obj.wingId)
  , ("name", Encode.string obj.name)
  , ("squadsList", Encode.list <| List.map encodeCompressedSquad obj.squadsList)
  ]
encodeCompressedSquad : CompressedSquad -> Encode.Value
encodeCompressedSquad obj = Encode.object
  [ ("fleetId", Encode.string obj.fleetId)
  , ("wingId", Encode.string obj.wingId)
  , ("squadId", Encode.string obj.squadId)
  , ("name", Encode.string obj.name)
  ]

encodeFleetEvent: FleetEvent -> Encode.Value
encodeFleetEvent obj =
  let
    (typefield, inner) = case obj of
      FleetEventFleetWarpChange obj2 -> ("FleetWarpChange", encodeFleetWarpChange obj2)
      FleetEventFreeMoveChange obj2 -> ("FreeMoveChange", encodeFreeMoveChange obj2)
      FleetEventLocationChange obj2 -> ("LocationChange", encodeLocationChange obj2)
      FleetEventMassLocationChange obj2 -> ("MassLocationChange", encodeMassLocationChange obj2)
      FleetEventMemberJoin obj2 -> ("MemberJoin", encodeMemberJoin obj2)
      FleetEventMemberPart obj2 -> ("MemberPart", encodeMemberPart obj2)
      FleetEventMotdChange obj2 -> ("MotdChange", encodeMotdChange obj2)
      FleetEventRegisteredChange obj2 -> ("RegisteredChange", encodeRegisteredChange obj2)
      FleetEventShipChange obj2 -> ("ShipChange", encodeShipChange obj2)
      FleetEventSquadMove obj2 -> ("SquadMove", encodeSquadMove obj2)
      FleetEventWingMove obj2 -> ("WingMove", encodeWingMove obj2)
    in
      Encode.object [(typefield, inner)]
encodeFleetWarpChange : FleetWarpChange -> Encode.Value
encodeFleetWarpChange obj = Encode.object
  [ ("id", encodeCompressedStandardIdentifier_Long_String obj.id)
  , ("old", Encode.bool obj.old)
  , ("now", Encode.bool obj.now)
  ]
encodeFreeMoveChange : FreeMoveChange -> Encode.Value
encodeFreeMoveChange obj = Encode.object
  [ ("old", Encode.bool obj.old)
  , ("now", Encode.bool obj.now)
  ]
encodeLocationChange : LocationChange -> Encode.Value
encodeLocationChange obj = Encode.object
  [ ("id", encodeCompressedStandardIdentifier_Long_String obj.id)
  , ("old", encodeCompressedLocation obj.old)
  , ("now", encodeCompressedLocation obj.now)
  ]
encodeCompressedLocation : CompressedLocation -> Encode.Value
encodeCompressedLocation obj = Encode.object
  [ ("solarSystem", Maybe.withDefault Encode.null <| Maybe.map encodeCompressedStandardIdentifier_Long_String obj.solarSystem)
  , ("station", Maybe.withDefault Encode.null <| Maybe.map encodeCompressedStandardIdentifier_Long_String obj.station)
  ]
encodeMassLocationChange : MassLocationChange -> Encode.Value
encodeMassLocationChange obj = Encode.object
  [ ("ids", Encode.list <| List.map encodeCompressedStandardIdentifier_Long_String obj.ids)
  , ("old", encodeCompressedLocation obj.old)
  , ("now", encodeCompressedLocation obj.now)
  ]
encodeMemberJoin : MemberJoin -> Encode.Value
encodeMemberJoin obj = Encode.object
  [ ("id", encodeCompressedStandardIdentifier_Long_String obj.id)
  , ("now", encodeCompressedMember obj.now)
  ]
encodeMemberPart : MemberPart -> Encode.Value
encodeMemberPart obj = Encode.object
  [ ("id", encodeCompressedStandardIdentifier_Long_String obj.id)
  , ("old", encodeCompressedMember obj.old)
  ]
encodeMotdChange : MotdChange -> Encode.Value
encodeMotdChange obj = Encode.object
  [ ("old", Encode.string obj.old)
  , ("now", Encode.string obj.now)
  ]
encodeRegisteredChange : RegisteredChange -> Encode.Value
encodeRegisteredChange obj = Encode.object
  [ ("old", Encode.bool obj.old)
  , ("now", Encode.bool obj.now)
  ]
encodeShipChange : ShipChange -> Encode.Value
encodeShipChange obj = Encode.object
  [ ("id", encodeCompressedStandardIdentifier_Long_String obj.id)
  , ("old", encodeCompressedStandardIdentifier_Long_String obj.old)
  , ("now", encodeCompressedStandardIdentifier_Long_String obj.now)
  , ("where", encodeCompressedLocation obj.where_escaped)
  ]
encodeSquadMove : SquadMove -> Encode.Value
encodeSquadMove obj = Encode.object
  [ ("id", encodeCompressedStandardIdentifier_Long_String obj.id)
  , ("old", encodeCompressedSquad obj.old)
  , ("now", encodeCompressedSquad obj.now)
  ]
encodeWingMove : WingMove -> Encode.Value
encodeWingMove obj = Encode.object
  [ ("id", encodeCompressedStandardIdentifier_Long_String obj.id)
  , ("old", encodeCompressedWing obj.old)
  , ("now", encodeCompressedWing obj.now)
  ]
encodeServerError : ServerError -> Encode.Value
encodeServerError obj = Encode.object
  [ ("error", encodeEveException obj.error)
  ]

encodeEveException: EveException -> Encode.Value
encodeEveException obj =
  let
    (typefield, inner) = case obj of
      EveExceptionAccessDeniedForbiddenError obj2 -> ("AccessDeniedForbiddenError", encodeAccessDeniedForbiddenError obj2)
      EveExceptionForbiddenError obj2 -> ("ForbiddenError", encodeForbiddenError obj2)
      EveExceptionUnauthorizedError obj2 -> ("UnauthorizedError", encodeUnauthorizedError obj2)
      EveExceptionUnsupportedMediaTypeError obj2 -> ("UnsupportedMediaTypeError", encodeUnsupportedMediaTypeError obj2)
    in
      Encode.object [(typefield, inner)]
encodeAccessDeniedForbiddenError : AccessDeniedForbiddenError -> Encode.Value
encodeAccessDeniedForbiddenError obj = Encode.object
  [ ("key", Encode.string obj.key)
  , ("isLocalized", Encode.bool obj.isLocalized)
  , ("message", Encode.string obj.message)
  ]
encodeForbiddenError : ForbiddenError -> Encode.Value
encodeForbiddenError obj = Encode.object
  [ ("isLocalized", Encode.bool obj.isLocalized)
  , ("key", Encode.string obj.key)
  , ("message", Encode.string obj.message)
  , ("title", Maybe.withDefault Encode.null <| Maybe.map Encode.string obj.title)
  ]
encodeUnauthorizedError : UnauthorizedError -> Encode.Value
encodeUnauthorizedError obj = Encode.object
  [ ("key", Encode.string obj.key)
  , ("isLocalized", Encode.bool obj.isLocalized)
  , ("message", Encode.string obj.message)
  ]
encodeUnsupportedMediaTypeError : UnsupportedMediaTypeError -> Encode.Value
encodeUnsupportedMediaTypeError obj = Encode.object
  [ ("key", Encode.string obj.key)
  , ("message", Encode.string obj.message)
  ]