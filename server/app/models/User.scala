package models

import com.mohiva.play.silhouette.api.{ Identity, LoginInfo }

case class User(
  name: String,
  id: String,
  loginInfo: LoginInfo
) extends Identity
