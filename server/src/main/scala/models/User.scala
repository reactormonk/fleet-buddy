package models
import oauth._

case class User(
  id: Long,
  name: String,
  token: OAuth2Token
)
