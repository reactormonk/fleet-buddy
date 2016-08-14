module Style exposing (..)

import Css exposing (..)
import Css.Elements exposing (..)
import Css.Namespace exposing (namespace)


type CssClasses
    = Form



-- type CssIds
--     = Page


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
            ]
