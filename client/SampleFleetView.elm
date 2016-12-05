module SampleFleetView exposing (..)

import Html.App as App
import Task exposing (..)
import Html exposing (..)
import FleetView
import Http
import Codec


type Model
    = ActualModel FleetView.FleetModel
    | Error String
    | Empty


type Action
    = SampleData FleetView.FleetModel
    | FleetViewAction FleetView.Action
    | HttpError Http.Error


init : ( Model, Cmd Action )
init =
    Empty ! [ Http.get Codec.decodeFleetUpdates "/api/fleetstate/random" |> perform (\error -> HttpError error) (\msg -> SampleData <| FleetView.FleetModel msg.state msg.events) ]


update : Action -> Model -> ( Model, Cmd Action )
update action model =
    case action of
        SampleData data ->
            ActualModel data ! []

        FleetViewAction action ->
            -- Ignore for now.
            model ! []

        HttpError error ->
            (Error <| toString error) ! []



-- HttpError string ->
--     Error string


view : Model -> Html Action
view model =
    case model of
        ActualModel data ->
            App.map FleetViewAction <| FleetView.fleetView data

        Empty ->
            div [] [ text "Loading..." ]

        Error error ->
            div [] [ text error ]
