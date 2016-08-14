module Landing exposing (..)

import List
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onInput, onSubmit)
import Regex exposing (..)
import Style exposing (..)
import Html.CssHelpers
import String


type alias Model =
    { enteredFleetUrl : String
    }


type ToParent
    = SwitchToFleet String


type Action
    = EnteredFleetUrl String
    | FleetUrlSubmit


{ id, class, classList } =
    Html.CssHelpers.withNamespace ""
init : Model
init =
    { enteredFleetUrl = "" }


update : Action -> Model -> ( Model, Maybe ToParent )
update msg model =
    case msg of
        EnteredFleetUrl url ->
            ( { model | enteredFleetUrl = url }, Nothing )

        FleetUrlSubmit ->
            ( model, Maybe.map SwitchToFleet <| parseUrl model.enteredFleetUrl )


view : Model -> Html.Html Action
view model =
    div [ classList [ ( "ui", True ), ( "middle", True ), ( "aligned", True ), ( "center", True ), ( "aligned", True ), ( "grid", True ) ] ]
        [ div
            [ class [ Style.Form ] ]
            [ Html.form
                [ classList [ ( "form", True ), ( "large", True ), ( "ui", True ) ] ]
                [ div
                    [ classList [ ( "field", True ), ( "stacked", True ), ( "segment", True ) ] ]
                    [ label [] [ Html.text "Fleet URL" ]
                    , input [ type' "text", placeholder "From fleet dropdown", onInput EnteredFleetUrl ] []
                    ]
                , button [ classList [ ( "ui", True ), ( "button", True ) ], type' "submit", viewValidation model, onSubmit FleetUrlSubmit ] [ Html.text "Go!" ]
                ]
            ]
        ]


parseUrl : String -> Maybe String
parseUrl entered =
    let
        reg =
            regex "\\d+/$"
    in
        Regex.find (AtMost 1) reg entered |> List.map .match |> List.head |> (Maybe.map <| String.dropRight 1)


viewValidation : Model -> Attribute msg
viewValidation model =
    case parseUrl model.enteredFleetUrl of
        Just id ->
            disabled False

        Nothing ->
            disabled True
