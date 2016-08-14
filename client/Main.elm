module Main exposing (..)

import FleetView
import Html
import Landing
import Maybe
import Navigation exposing (Location)
import Result.Extra exposing (mapBoth)
import RouteUrl
import RouteUrl exposing (UrlChange)
import RouteUrl.Builder exposing (..)
import UrlParser exposing (..)
import Tuple2 exposing (mapEach, mapFst, mapSnd)
import Html.App as App
import String


main : Program Flags
main =
    RouteUrl.programWithFlags
        { delta2url = delta2url
        , location2messages = location2messages
        , init = init
        , update = update
        , view = view
        , subscriptions = subscriptions
        }


type alias Model =
    { view : ViewModel
    , host : String
    , protocol : String
    }


type ViewModel
    = LandingModel Landing.Model
    | FleetViewModel FleetView.Model
    | NotFound


type alias Flags =
    { host : String
    , protocol : String
    }


init : Flags -> ( Model, Cmd Action )
init flags =
    ( { view = LandingModel Landing.init, host = flags.host, protocol = flags.protocol }, Cmd.none )


update : Action -> Model -> ( Model, Cmd Action )
update action model =
    let
        ( newModel, cmd ) =
            case model.view of
                LandingModel loadingM ->
                    case action of
                        Switch ac ->
                            switch ac

                        FleetViewAction _ ->
                            ( LandingModel loadingM, Cmd.none )

                        LandingAction ac ->
                            case Landing.update ac loadingM of
                                ( m, Just landingAction ) ->
                                    case landingAction of
                                        Landing.SwitchToFleet id ->
                                            ( FleetViewModel (FleetView.init { id = id, host = model.host, protocol = model.protocol }), Cmd.none )

                                ( m, Nothing ) ->
                                    ( LandingModel loadingM, Cmd.none )

                FleetViewModel model ->
                    case action of
                        Switch ac ->
                            switch ac

                        FleetViewAction ac ->
                            FleetView.update ac model |> mapEach FleetViewModel (Cmd.map FleetViewAction)

                        LandingAction _ ->
                            ( FleetViewModel model, Cmd.none )

                NotFound ->
                    case action of
                        Switch ac ->
                            switch ac

                        FleetViewAction _ ->
                            model.view ! []

                        LandingAction _ ->
                            model.view ! []
    in
        ( { model | view = newModel }, cmd )


switch : SwitchAction -> ( ViewModel, Cmd Action )
switch action =
    case action of
        SwitchToLandingPage ->
            LandingModel Landing.init ! []

        SwitchToFleetPage init ->
            ( FleetViewModel <| FleetView.init init, Cmd.none )

        UrlNotFound ->
            ( NotFound, Cmd.none )


type SwitchAction
    = SwitchToLandingPage
    | SwitchToFleetPage FleetView.FleetInit
    | UrlNotFound


type Action
    = Switch SwitchAction
    | FleetViewAction FleetView.Action
    | LandingAction Landing.Action


location2messages : Location -> List Action
location2messages location =
    [ (location2page location) |> mapBoth (\_ -> UrlNotFound) identity >> Switch ]


location2page : Location -> Result String SwitchAction
location2page location =
    parse identity
        (oneOf
            [ format (\id -> SwitchToFleetPage { id = id, host = location.host, protocol = location.protocol }) (s "fleet" </> string)
            , format SwitchToLandingPage (s "")
            ]
        )
    <|
        (String.dropLeft 1 location.pathname)


delta2url : Model -> Model -> Maybe UrlChange
delta2url previous current =
    Maybe.map toUrlChange <|
        delta2builder previous.view current.view


delta2builder : ViewModel -> ViewModel -> Maybe Builder
delta2builder previous current =
    case current of
        LandingModel current ->
            Just (builder |> newEntry)

        FleetViewModel current ->
            case previous of
                LandingModel previous ->
                    Just (FleetView.urlBuilder current.id)

                FleetViewModel previous ->
                    if not (previous.id == current.id) then
                        Just (FleetView.urlBuilder current.id)
                    else
                        Nothing

                NotFound ->
                    Just (FleetView.urlBuilder current.id)

        NotFound ->
            Nothing


subscriptions : Model -> Sub Action
subscriptions model =
    case model.view of
        FleetViewModel model ->
            Sub.map FleetViewAction <| FleetView.subscriptions model

        LandingModel model ->
            Sub.none

        NotFound ->
            Sub.none


view : Model -> Html.Html Action
view model =
    case model.view of
        LandingModel model ->
            App.map LandingAction <| Landing.view model

        FleetViewModel model ->
            App.map FleetViewAction <| FleetView.view model

        NotFound ->
            Html.text "404"
