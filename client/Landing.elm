module Landing exposing (..)

import List
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onInput, onClick)
import Regex exposing (..)


type alias Model =
    { enteredFleetUrl : String
    }

type ToParent = SwitchToFleet String

type Action
    = EnteredFleetUrl String
    | FleetUrlSubmit


init : Model
init =
    { enteredFleetUrl = "" }


update : Action -> Model -> ( Model, Maybe ToParent )
update msg model =
    case msg of
        EnteredFleetUrl url ->
            ({ model | enteredFleetUrl = url }, Nothing)

        FleetUrlSubmit ->
            (model, Maybe.map SwitchToFleet <| parseUrl model.enteredFleetUrl )


view : Model -> Html.Html Action
view model =
    div []
        [ Html.form
            [ class "form", class "ui" ]
            [ div
                [ class "field" ]
                [ label [] [ Html.text "Fleet URL" ]
                , input [ type' "text", placeholder "Fleet URL from fleet dropdown.", onInput EnteredFleetUrl ] []
                ]
            , button [ class "ui", class "button", type' "submit", viewValidation model, onClick FleetUrlSubmit ] [ Html.text "Go!" ]
            ]
        ]


parseUrl : String -> Maybe String
parseUrl entered =
    let
        reg =
            regex "\\d+$"
    in
        Regex.find (AtMost 1) reg entered |> List.map .match |> List.head


viewValidation : Model -> Attribute msg
viewValidation model =
    case parseUrl model.enteredFleetUrl of
        Just id ->
            disabled False

        Nothing ->
            disabled True
