module EnterFleetUrl exposing (..)

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
    div [ class [ "ui", "middle", "aligned", "center", "aligned", "grid" ] ]
        [ div
            [ class [ Style.Form ] ]
            [ Html.form
                [ class [ "form", "large", "ui" ], onSubmit FleetUrlSubmit ]
                [ div
                    [ class [ "field", "stacked", "segment" ] ]
                    [ label [] [ Html.text "Fleet URL" ]
                    , input [ type' "text", placeholder "https://crest-tq.eveonline.com/fleets/00000000000/", onInput EnteredFleetUrl, style [ ( "width", "27em" ) ] ] []
                    ]
                , button [ class [ "ui", "button" ], type' "submit", viewValidation model ] [ Html.text "Go!" ]
                ]
            , div [ class [ "ui", "hidden", "divider" ] ] []
            , div [ class [ "video" ] ]
                [ video [ autoplay True, loop True ]
                    [ source [ src "/fleet.webm", type' "video/webm" ] []
                    ]
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
