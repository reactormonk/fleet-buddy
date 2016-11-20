import sys.process._
import doobie.imports._
import scalaz.concurrent.Task

object StaticDataFromSbt extends StaticData {
  def main(args: Array[String]): Unit = {
    val file = args(0)
    val suffix = args.toList.lift(1).getOrElse("")
    val user = buildInfo.BuildInfo.flywayUser
    val password = buildInfo.BuildInfo.flywayPassword
    val url = buildInfo.BuildInfo.flywayUrl + suffix
    val db = url.split(":").last
    val xa = DriverManagerTransactor[Task](buildInfo.BuildInfo.flywayDriver, url, user, password)
    load(file, user, db, password, xa)
  }
}

object StaticDataFromBash extends StaticData {
  val buddyTask = controllers.Loader.buddy
  val dbconfigTask = controllers.Loader.load.map(controllers.Loader.db)

  def main(args: Array[String]): Unit = {
    val file = args(0)
    val t = for {
      buddy <- buddyTask
      dbconfig <- dbconfigTask
    } yield {
      load(file, dbconfig.user, dbconfig.url.split(":").last, dbconfig.password, buddy.xa)
    }
    t.unsafePerformSync
  }
}

trait StaticData {
  val alter: Update0 = sql"""
alter table "mapSolarSystems"
alter column "solarSystemName" set not null;

alter table "invTypes"
alter column "typeName" set not null;

alter table "staStations"
alter column "stationName" set not null;

alter table "staStations"
alter column "solarSystemID" set not null;
""".update

  def load(file: String, user: String, db: String, password: String, xa: Transactor[Task]) = {
    restore(file, user, db, password)
    alter.run.transact(xa).unsafePerformSync
  }

  def restore(file: String, user: String, db: String, password: String) = {
    Process(List("pg_restore", "-c", "--no-password", "-U", user, "-d", db, file), None, ("PGPASSWORD", password)).!
  }
}
