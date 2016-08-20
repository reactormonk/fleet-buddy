module Style exposing (..)

import Css exposing (..)
import Css.Elements exposing (..)
import Css.Namespace exposing (namespace)


type CssClasses
    = Form


type CssIds
    = FleetFeed
    | FleetShipOverview
    | FleetViewContainer


css : Stylesheet
css =
    stylesheet <|
        (namespace "")
            [ body
                []
            , (.) Form
                [ marginTop (em 10)
                ]
            , (#) FleetViewContainer
                [ margin (em 1)
                ]
            , (#) FleetFeed
                []
            , (#) FleetShipOverview
                []
            ]
