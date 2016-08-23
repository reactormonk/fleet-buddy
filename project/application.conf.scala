import org.flywaydb.sbt.FlywayPlugin.autoImport._
import knobs._

object ApplicationConf {
  val cfg = loadImmutable(Required(FileResource(new java.io.File("application.conf"))) :: Nil).unsafePerformSync
  val settings = Seq(
        flywayUrl := cfg.require[String]("db.url")
      , flywayDriver := "org.postgresql.Driver"
      , flywayUser := cfg.require[String]("db.user")
      , flywayPassword := cfg.require[String]("db.password")
  )
}
