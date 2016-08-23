import org.flywaydb.core.Flyway
import controllers.Loader

object Migrate {
  def main(args: Array[String]): Unit = {
    val dbcfg = Loader.load.map(Loader.db).unsafePerformSync
    val flyway = new Flyway()
    flyway.setDataSource(dbcfg.url, dbcfg.user, dbcfg.password)
    flyway.migrate
  }
}
