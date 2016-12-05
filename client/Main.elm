module Main exposing (..)

import FleetView
import Html
import EnterFleetUrl
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
import SampleFleetView
import Codec
import Json.Decode


main : Program Flags
main =
    RouteUrl.programWithFlags
        { delta2url = delta2url
        , location2messages = location2messages
        , init = init
        , update = (Debug.log "action") >> update
        , view = view
        , subscriptions = subscriptions
        }


type alias Model =
    { view : ViewModel
    , host : String
    , protocol : String
    }


type ViewModel
    = EnterFleetUrlModel EnterFleetUrl.Model
    | FleetViewModel FleetView.Model
    | SampleFleetViewModel SampleFleetView.Model
    | NotFound


type alias Flags =
    { host : String
    , protocol : String
    , sample : Maybe String
    }


init : Flags -> ( Model, Cmd Action )
init flags =
    ( { view =
            case flags.sample of
                Just string ->
                    SampleFleetViewModel <|
                        case Json.Decode.decodeString Codec.decodeFleetUpdates string of
                            Ok model ->
                                SampleFleetView.ActualModel model

                            Err error ->
                                SampleFleetView.Error error

                Nothing ->
                    EnterFleetUrlModel EnterFleetUrl.init
      , host = flags.host
      , protocol = flags.protocol
      }
    , Cmd.none
    )


update : Action -> Model -> ( Model, Cmd Action )
update baseAction model =
    let
        ( newModel, cmd ) =
            case baseAction of
                Switch ac ->
                    switch ac

                VAction action ->
                    let
                        res =
                            case model.view of
                                EnterFleetUrlModel landingM ->
                                    case action of
                                        EnterFleetUrlAction ac ->
                                            case EnterFleetUrl.update ac landingM of
                                                ( m, Just landingAction ) ->
                                                    case landingAction of
                                                        EnterFleetUrl.SwitchToFleet id ->
                                                            ( FleetViewModel (FleetView.init { id = id, host = model.host, protocol = model.protocol }), Cmd.none )

                                                ( m, Nothing ) ->
                                                    ( EnterFleetUrlModel m, Cmd.none )

                                        _ ->
                                            ( EnterFleetUrlModel landingM, Cmd.none )

                                FleetViewModel model ->
                                    case action of
                                        FleetViewAction ac ->
                                            FleetView.update ac model |> mapEach FleetViewModel (Cmd.map FleetViewAction)

                                        _ ->
                                            ( FleetViewModel model, Cmd.none )

                                SampleFleetViewModel model ->
                                    case action of
                                        SampleFleetViewAction ac ->
                                            SampleFleetView.update ac model |> mapEach SampleFleetViewModel (Cmd.map SampleFleetViewAction)

                                        _ ->
                                            ( SampleFleetViewModel model, Cmd.none )

                                NotFound ->
                                    case action of
                                        _ ->
                                            model.view ! []
                    in
                        mapSnd (Cmd.map VAction) res
    in
        ( { model | view = newModel }, cmd )


switch : SwitchAction -> ( ViewModel, Cmd Action )
switch action =
    case action of
        SwitchToEnterFleetUrlPage ->
            EnterFleetUrlModel EnterFleetUrl.init ! []

        SwitchToFleetPage init ->
            ( FleetViewModel <| FleetView.init init, Cmd.none )

        SwitchToSampleFleetView ->
            mapEach SampleFleetViewModel (Cmd.map <| SampleFleetViewAction >> VAction) SampleFleetView.init

        UrlNotFound ->
            ( NotFound, Cmd.none )


type SwitchAction
    = SwitchToEnterFleetUrlPage
    | SwitchToFleetPage FleetView.FleetInit
    | SwitchToSampleFleetView
    | UrlNotFound


type Action
    = Switch SwitchAction
    | VAction ViewAction


type ViewAction
    = FleetViewAction FleetView.Action
    | SampleFleetViewAction SampleFleetView.Action
    | EnterFleetUrlAction EnterFleetUrl.Action


location2messages : Location -> List Action
location2messages location =
    [ (location2page location) |> mapBoth (\_ -> UrlNotFound) identity >> (Debug.log "switching") >> Switch ]


location2page : Location -> Result String SwitchAction
location2page location =
    parse identity
        (oneOf
            [ format (\id -> SwitchToFleetPage { id = id, host = location.host, protocol = location.protocol }) (s "fleet" </> string)
            , format SwitchToSampleFleetView (s "sample")
            , format SwitchToEnterFleetUrlPage (s "")
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
        EnterFleetUrlModel current ->
            Just (builder |> newEntry)

        FleetViewModel current ->
            case previous of
                FleetViewModel previous ->
                    if not (previous.id == current.id) then
                        Just (FleetView.urlBuilder current.id)
                    else
                        Nothing

                _ ->
                    Just (FleetView.urlBuilder current.id)

        SampleFleetViewModel current ->
            Just (builder |> newEntry |> appendToPath [ "sample" ])

        NotFound ->
            Nothing


subscriptions : Model -> Sub Action
subscriptions model =
    case model.view of
        FleetViewModel model ->
            Sub.map (FleetViewAction >> VAction) <| FleetView.subscriptions model

        _ ->
            Sub.none


view : Model -> Html.Html Action
view model =
    case model.view of
        EnterFleetUrlModel model ->
            App.map (EnterFleetUrlAction >> VAction) <| EnterFleetUrl.view model

        FleetViewModel model ->
            App.map (FleetViewAction >> VAction) <| FleetView.view model

        SampleFleetViewModel model ->
            App.map (SampleFleetViewAction >> VAction) <| SampleFleetView.view model

        NotFound ->
            Html.text "404"
