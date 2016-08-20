module Style exposing (..)

import Css exposing (..)
import Css.Elements exposing (..)
import Css.Namespace exposing (namespace)


type CssClasses
    = Form


type CssIds
    = FleetFeed
    | FleetShipOverview


css : Stylesheet
css =
    stylesheet <|
        (namespace "")
            [ body
                [ backgroundColor (rgb 150 150 150)
                ]
            , (.) Form
                [ marginTop (px 100)
                ]
            , (#) FleetFeed
                [ backgroundColor (rgb 175 175 175)
                , margin (px 10)
                ]
            , (#) FleetShipOverview
                [ margin (px 10)
                ]
            ]
