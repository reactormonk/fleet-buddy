package models
import oauth._
import doobie.imports._
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import shapeless._
import java.time._
import utils.Doobie._

case class User(
  id: Long,
  name: String,
  token: OAuth2Token
)

object User {
  def upsertQuery(user: User): Update0 = {
    val oauth = user.token
    sql"""
insert into users
  (id, name, access_token, token_type, expires_in, refresh_token, generatedAt)
values
  (${user.id}, ${user.name}, ${oauth.access_token}, ${oauth.token_type}, ${oauth.expires_in}, ${oauth.refresh_token}, ${oauth.generatedAt})
on conflict on constraint users_pkey
do update set
  name = ${user.name},
  access_token = ${oauth.access_token},
  token_type = ${oauth.token_type},
  expires_in = ${oauth.expires_in},
  refresh_token = ${oauth.refresh_token},
  generatedAt = ${oauth.generatedAt}
where users.id = ${user.id}
""".update
  }
  def upsert(user: User): ConnectionIO[Unit] = upsertQuery(user).run.map(_ => ())

  def selectQuery(id: Long): Query0[User] =
    sql"select id, name, access_token, token_type, expires_in, refresh_token, generatedAt from users where id = $id"
      .query[User]
  def selectQuery(name: String): Query0[User] =
    sql"select id, name, access_token, token_type, expires_in, refresh_token, generatedAt from users where name = $name"
      .query[User]
  def load(id: Long): ConnectionIO[Option[User]] = selectQuery(id).option
  /*
   ** Not indexed
   */
  def load(name: String): ConnectionIO[Option[User]] = selectQuery(name).option
  def listQuery: Query0[Long] =
    sql"select id from users".query[Long]
  def listUsers: ConnectionIO[List[Long]] = listQuery.list
}
