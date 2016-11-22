module FleetView exposing (..)

import Maybe exposing (..)
import Codec exposing (..)
import List
import Dict exposing (Dict)
import Dict.Extra exposing (groupBy)
import Html exposing (..)
import Html.Attributes exposing (..)
import WebSocket
import Json.Decode exposing (decodeString)
import Json.Encode exposing (encode)
import Result.Extra exposing (mapBoth)
import RouteUrl.Builder exposing (..)
import Html.CssHelpers
import Style exposing (..)


type alias FleetInit =
    { id : String, host : String, protocol : String }


type alias FleetSocket =
    { url : String }


type alias Model =
    { id : String
    , socket : FleetSocket
    , data : Maybe FleetModel
    , running : Bool
    }


type alias FleetModel =
    { state : FleetState
    , events : List FleetEvent
    }


type Action
    = FromServer ServerToClient
    | InvalidMessage String


{ id, class, classList } =
    Html.CssHelpers.withNamespace ""


init : FleetInit -> Model
init struct =
    { id = struct.id, data = Nothing, socket = fleetSocket struct, running = True }


update : Action -> Model -> ( Model, Cmd Action )
update action model =
    case action of
        FromServer msg ->
            case msg of
                ServerToClientFleetUpdates updates ->
                    case model.data of
                        Just current ->
                            { model | data = Just { state = updates.state, events = List.take 100 (updates.events ++ current.events) } } ! []

                        Nothing ->
                            { model | data = Just { state = updates.state, events = List.take 100 updates.events } } ! []

                ServerToClientServerError error ->
                    Debug.log "error from server: " error |> (\_ -> ( model, Cmd.none ))

                ServerToClientEndOfStream _ ->
                    { model | running = False } ! []

        InvalidMessage _ ->
            ( model, Cmd.none )


insertOrUpdate : comparable -> (Maybe a -> a) -> Dict comparable a -> Dict comparable a
insertOrUpdate key fun dict =
    Dict.insert key (fun (Dict.get key dict)) dict


extractLocation : CompressedMember -> CompressedLocation
extractLocation member =
    { solarSystem = Just member.solarSystem, station = member.station }


count : List comparable -> Dict comparable Int
count list =
    List.foldr
        (\elem dict ->
            insertOrUpdate elem
                (\count -> (Maybe.withDefault 0 count) + 1)
                dict
        )
        Dict.empty
        list


list : a -> List a
list elem =
    [ elem ]


shipImage : String -> String
shipImage id =
    "https://image.eveonline.com/Render/" ++ id ++ "_512.png"


characterImage : String -> String
characterImage id =
    "https://image.eveonline.com/Character/" ++ id ++ "_512.jpg"


shipText : { b | count : String, name : String } -> String
shipText ship =
    ship.count ++ "x " ++ ship.name


renderCharacterInList : { a | id : String, name : String, location : CompressedLocation } -> Html Action
renderCharacterInList character =
    div [ class [ "item" ] ]
        [ img [ class [ "ui", "image", "mini" ], src (characterImage character.id) ] []
        , div [ class [ "content" ] ]
            [ div [ class [ "ui", "sub", "header" ] ]
                [ text character.name ]
            , renderLocation character.location
            ]
        ]


renderCharacterList : List { a | id : String, name : String, location : CompressedLocation } -> Html Action
renderCharacterList list =
    div [ class [ "ui", "horizontal", "list" ] ] <| List.map renderCharacterInList list


renderShipListShip : ( { a | id : String, name : String }, Int, List { b | id : String, name : String, location : CompressedLocation } ) -> Html Action
renderShipListShip ( ship, count, members ) =
    div [ class [ "item" ] ]
        [ div [ class [ "image" ] ] [ img [ src (shipImage ship.id), class [ "ui", "image", "small" ] ] [] ]
        , div [ class [ "content" ] ]
            [ div
                [ class [ "header" ] ]
                [ text (shipText { name = ship.name, count = toString count }) ]
            , renderCharacterList members
            ]
        ]


renderShipList : List ( { a | id : String, name : String }, Int, List { b | id : String, name : String, location : CompressedLocation } ) -> Html Action
renderShipList list =
    div [ class [ "ui", "list", "relaxed" ] ] <| List.map renderShipListShip list


renderEvent' : Maybe { a | id : String, name : String } -> List (Html Action) -> List (Html Action)
renderEvent' character summary =
    case character of
        Just char ->
            [ div [ class [ "label" ] ] [ img [ src (characterImage char.id) ] [] ]
            , div [ class [ "content" ] ] [ div [ class [ "summary" ] ] <| [ text char.name ] ++ summary ]
            ]

        Nothing ->
            [ div [ class [ "content" ] ] [ div [ class [ "summary" ] ] summary ] ]


renderChange : Bool -> String
renderChange b =
    if b then
        "on"
    else
        "off"


renderStation : CompressedStandardIdentifier_Long_String -> CompressedStandardIdentifier_Long_String -> List (Html Action)
renderStation station solarSystem =
    [ text <| station.name ++ " in " ++ solarSystem.name ]


renderSolarSystem : CompressedStandardIdentifier_Long_String -> List (Html Action)
renderSolarSystem solarSystem =
    [ text solarSystem.name ]


renderLocation : CompressedLocation -> Html Action
renderLocation location =
    case ( location.solarSystem, location.station ) of
        ( Just solarSystem, Nothing ) ->
            text solarSystem.name

        ( Just solarSystem, Just station ) ->
            text <| station.name ++ ", " ++ solarSystem.name

        ( Nothing, _ ) ->
            text "logged out"


renderLocationChange : { a | old : CompressedLocation, now : CompressedLocation } -> List (Html Action)
renderLocationChange change =
    case ( change.old.solarSystem, change.old.station, change.now.solarSystem, change.now.station ) of
        ( Nothing, Nothing, solarSystem, station ) ->
            [ text " logged in to " ] ++ [ renderLocation { solarSystem = solarSystem, station = station } ]

        ( solarSystem, station, Nothing, Nothing ) ->
            [ text " logged out from " ] ++ [ renderLocation { solarSystem = solarSystem, station = station } ]

        ( Just old, Nothing, Just now, Nothing ) ->
            [ text " jumped from " ] ++ renderSolarSystem old ++ [ text " to " ] ++ renderSolarSystem now

        ( Just old, Just oldStation, Just now, Nothing ) ->
            let
                to =
                    if old == now then
                        []
                    else
                        [ text " and jumped into " ] ++ renderSolarSystem now
            in
                [ text " undocked from " ]
                    ++ renderStation oldStation old
                    ++ to

        ( Just old, Just oldStation, Just now, Just nowStation ) ->
            [ text " switched stations from " ] ++ renderStation oldStation old ++ [ text " to " ] ++ renderStation nowStation now

        ( Just old, Nothing, Just now, Just nowStation ) ->
            let
                from =
                    if old == now then
                        []
                    else
                        [ text " jumped in from " ] ++ renderSolarSystem old ++ [ text " and" ]
            in
                from ++ [ text " docked in " ] ++ renderStation nowStation now

        ( _, _, Nothing, _ ) ->
            [ text " is in a station off the map" ]

        ( Nothing, _, _, _ ) ->
            [ text " was in a station off the map" ]


renderEvent : FleetEvent -> Html Action
renderEvent event =
    let
        inner =
            case event of
                FleetEventFleetWarpChange change ->
                    renderEvent' (Just change.id) <| list <| text <| " switched his fleet warp " ++ (renderChange change.now)

                FleetEventFreeMoveChange change ->
                    renderEvent' Nothing <| list <| text <| "Free move is now " ++ (renderChange change.now)

                FleetEventLocationChange change ->
                    renderEvent' (Just change.id) <| renderLocationChange change

                FleetEventMassLocationChange change ->
                    renderEvent' Nothing <| [ text <| toString <| List.length change.ids ] ++ renderLocationChange change

                FleetEventMemberJoin join ->
                    renderEvent' (Just join.id) <| list <| text <| " joined your fleet."

                FleetEventMemberPart part ->
                    renderEvent' (Just part.id) <| list <| text <| " left your fleet."

                FleetEventMotdChange change ->
                    renderEvent' Nothing <| list <| text <| "The motd is now " ++ change.now

                FleetEventRegisteredChange change ->
                    renderEvent' Nothing <| list <| text <| "Fleet registration is now " ++ (renderChange change.now)

                FleetEventShipChange change ->
                    renderEvent' (Just change.id) <| list <| text <| " switched from a " ++ change.old.name ++ " to a " ++ change.now.name

                FleetEventSquadMove move ->
                    renderEvent' (Just move.id) <| list <| text <| " moved from Squad " ++ move.old.name ++ " to Squad " ++ move.now.name

                FleetEventWingMove move ->
                    renderEvent' (Just move.id) <| list <| text <| " moved from Wing " ++ move.old.name ++ " to Wing " ++ move.now.name
    in
        div [ class [ "event" ] ] inner


view : Model -> Html Action
view model =
    case model.running of
        True ->
            case model.data of
                Just data ->
                    let
                        countedShips =
                            data.state.members
                                |> List.map (\m -> ( m.ship.id, m.ship.name ))
                                |> count
                                |> Dict.toList
                                |> List.sortBy snd
                                |> List.reverse

                        membersByShip =
                            data.state.members
                                |> List.map (\m -> { name = m.character.name, id = m.character.id, location = (extractLocation m), shipId = m.ship.id })
                                |> Dict.Extra.groupBy .shipId

                        ships =
                            List.map (\( ( id, name ), count ) -> ( { id = id, name = name }, count, Maybe.withDefault [] <| Dict.get id membersByShip )) countedShips
                    in
                        div [ id FleetViewContainer ]
                            [ div [ class [ "ui", "two", "column", "grid" ] ]
                                [ div [ class [ "eleven", "wide", "column" ], id FleetShipOverview ]
                                    [ h1 [ class [ "ui", "dividing", "header" ] ] [ text "Ships" ]
                                    , renderShipList ships
                                    ]
                                , div [ class [ "five", "wide", "column" ], id FleetFeed ]
                                    [ h1 [ class [ "ui", "dividing", "header" ] ] [ text "Feed" ]
                                    , div [ class [ "ui", "feed" ] ] <|
                                        List.map renderEvent data.events
                                    ]
                                ]
                            ]

                Nothing ->
                    div []
                        [ text "Loading..." ]

        False ->
            div []
                [ text "Fleet's over or you lost boss. Reload as needed." ]


send : Model -> ClientToServer -> Cmd msg
send model message =
    WebSocket.send model.socket.url (encodeClientToServer message |> encode 0)


fleetSocket : FleetInit -> FleetSocket
fleetSocket struct =
    let
        proto =
            if struct.protocol == "http:" then
                "ws"
            else
                "wss"
    in
        FleetSocket <|
            proto
                ++ "://"
                ++ struct.host
                ++ "/api/fleet-ws/"
                ++ struct.id


subscriptions : Model -> Sub Action
subscriptions model =
    if model.running then
        WebSocket.listen model.socket.url (decodeString decodeServerToClient >> mapBoth InvalidMessage FromServer)
    else
        Sub.none


urlBuilder : String -> Builder
urlBuilder id =
    (builder |> newEntry |> appendToPath [ "fleet", id ])
