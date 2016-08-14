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
    {id: String, host: String, protocol: String }

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


imageTemplate : Template.Template { b | id : String }
imageTemplate =
    template "https://image.eveonline.com/Render/"
        |> withValue .id
        |> withString "_512.png"


shipTextTemplate : Template.Template { b | count : String, name : String }
shipTextTemplate =
    template ""
        |> withValue .count
        |> withString "x "
        |> withValue .name


renderShip : CompressedStandardIdentifier_Long_String -> Int -> Html Action
renderShip ship count =
    div [ classList [ ( "ui", True ), ( "card", True ) ] ]
        [ div [ class "image" ]
            [ img [ src (render imageTemplate ship) ] []
            , div [ class "content" ]
                [ div [ class "header" ] [ text (render shipTextTemplate { name = ship.name, count = toString count }) ] ]
            ]
        ]


view : Model -> Html Action
view model =
    case model.data of
        Just current ->
            let
                countedShips =
                    current.state.members
                        |> List.map (\m -> ( m.ship.id, m.ship.name ))
                        |> count
                        |> Dict.toList
                        |> List.map (\ ( ( id, name ), cnt ) -> renderShip { id = id, name = name } cnt)
            in
                div []
                    [ div [ classList [ ( "ui", True ), ( "link", True ), ( "cards", True ) ] ] countedShips ]

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
            if struct.protocol == "http" then
                "ws"
            else
                "wss"
    in
        FleetSocket <| proto
            ++ "://"
            ++ struct.host
            ++ "/fleet-ws/"
            ++ struct.id


subscriptions : Model -> Sub Action
subscriptions model =
    WebSocket.listen model.socket.url (decodeString decodeServerToClient >> mapBoth InvalidMessage FromServer)

urlBuilder: String -> Builder
urlBuilder id = (builder |> newEntry |> appendToPath [ "fleet", id ])
