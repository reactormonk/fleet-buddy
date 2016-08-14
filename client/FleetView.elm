module FleetView exposing (..)

import Maybe exposing (..)
import Codec exposing (..)
import List
import Dict exposing (Dict)
import Html exposing (..)
import Html.Attributes exposing (..)
import Template exposing (template, render, withValue, withString)
import WebSocket
import Json.Decode exposing (decodeString)
import Json.Encode exposing (encode)
import Result.Extra exposing (mapBoth)
import RouteUrl.Builder exposing (..)


type alias FleetInit =
    { id : String, host : String, protocol : String }


type alias FleetSocket =
    { url : String }


type alias Model =
    { id : String
    , socket : FleetSocket
    , data : Maybe FleetModel
    }


type alias FleetModel =
    { state : FleetState
    , events : List FleetEvent
    }


type Action
    = FromServer ServerToClient
    | InvalidMessage String


init : FleetInit -> Model
init struct =
    { id = struct.id, data = Nothing, socket = fleetSocket struct }


update : Action -> Model -> ( Model, Cmd Action )
update action model =
    case model.data of
        Just current ->
            case action of
                FromServer msg ->
                    case msg of
                        ServerToClientFleetUpdates updates ->
                            ( { model | data = Just { state = updates.state, events = List.take 100 (updates.events ++ current.events) } }, Cmd.none )

                InvalidMessage _ ->
                    ( model, Cmd.none )

        Nothing ->
            case action of
                FromServer msg ->
                    case msg of
                        ServerToClientFleetUpdates updates ->
                            ( { model | data = Just { state = updates.state, events = List.take 100 updates.events } }, Cmd.none )

                InvalidMessage _ ->
                    ( model, Cmd.none )


insertOrUpdate : comparable -> (Maybe a -> a) -> Dict comparable a -> Dict comparable a
insertOrUpdate key fun dict =
    Dict.insert key (fun (Dict.get key dict)) dict


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


shipImageTemplate : Template.Template { b | id : String }
shipImageTemplate type' =
    template "https://image.eveonline.com/"
        |> withString "Render"
        |> withString "/"
        |> withValue .id
        |> withString "_512.png"


characterImageTemplate : Template.Template { b | id : String }
characterImageTemplate type' =
    template "https://image.eveonline.com/"
        |> withString "Character"
        |> withString "/"
        |> withValue .id
        |> withString "_512.jpg"


shipTextTemplate : Template.Template { b | count : String, name : String }
shipTextTemplate =
    template ""
        |> withValue .count
        |> withString "x "
        |> withValue .name


renderShip : { a | id : String, name : String } -> Int -> Html Action
renderShip ship count =
    div [ classList [ ( "ui", True ), ( "card", True ) ] ]
        [ div [ class "image" ]
            [ img [ src (render shipImageTemplate ship) ] []
            , div [ class "content" ]
                [ div [ class "header" ] [ text (render shipTextTemplate { name = ship.name, count = toString count }) ] ]
            ]
        ]


renderEvent' : Maybe { a | id : String, name : String } -> List (Html Action) -> List (Html Action)
renderEvent' character summary =
    case character of
        Just char ->
            [ div [ class "label" ] [ img [ src (render characterImageTemplate char) ] [] ]
            , div [ class "content" ] [ div [ class "summary" ] <| [ text char.name ] ++ summary ]
            ]

        Nothing ->
            [ div [ class "content" ] [ div [ class "summary" ] summary ] ]


renderChange : Bool -> String
renderChange b =
    if b then
        "on"
    else
        "off"


renderLocation : CompressedLocation -> Html Action
renderLocation location =
    case location.solarSystem of
        Nothing ->
            text "logged out"

        Just solarSystem ->
            text <|
                "in "
                    ++ solarSystem.name
                    ++ (case location.station of
                            Nothing ->
                                ""

                            Just station ->
                                " docked in " ++ station.name
                       )


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
                    renderEvent' (Just change.id) [ (text " went from "), (renderLocation change.old), (text " to "), (renderLocation change.now) ]

                FleetEventMassLocationChange change ->
                    renderEvent' Nothing [ (text "A whole lot of people went from "), (renderLocation change.old), (text " to "), (renderLocation change.now) ]

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
        div [ class "event" ] inner


view : Model -> Html Action
view model =
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
                        |> List.map (\( ( id, name ), cnt ) -> renderShip { id = id, name = name } cnt)
            in
                div [ classList [ ( "ui", True ), ( "two", True ), ( "column", True ), ( "grid", True ) ] ]
                    [ div [ classList [ ( "ten", True ), ( "wide", True ), ( "column", True ) ] ]
                        [ div [ classList [ ( "ui", True ), ( "cards", True ) ] ] countedShips
                        ]
                    , div [ classList [ ( "five", True ), ( "wide", True ), ( "column", True ) ] ]
                        [ div [ classList [ ( "ui", True ), ( "feed", True ) ] ] <|
                            List.map renderEvent data.events
                        ]
                    ]

        Nothing ->
            div []
                [ text "Loading..." ]


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
                ++ "/fleet-ws/"
                ++ struct.id


subscriptions : Model -> Sub Action
subscriptions model =
    WebSocket.listen model.socket.url (decodeString decodeServerToClient >> mapBoth InvalidMessage FromServer)


urlBuilder : String -> Builder
urlBuilder id =
    (builder |> newEntry |> appendToPath [ "fleet", id ])
