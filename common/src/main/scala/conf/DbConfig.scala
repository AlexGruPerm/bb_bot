package conf

trait DbType
case object Postgresql extends DbType
case object Oracle     extends DbType
case object Clickhouse extends DbType
case object Unknown    extends DbType

case class DbConfig(
  driver: String,
  url: String,
  username: String,
  password: String,
  maximumPoolSize: Int,
  minimumIdle: Int,
  connectionTimeout: Int,
  poolName: String,
  autoCommit: Boolean
) {
  val dbType: DbType =
    driver match {
      case str if str.contains("postgresql") => Postgresql
      case str if str.contains("oracle")     => Oracle
      case str if str.contains("clickhouse") => Clickhouse
      case _                                 => throw new Exception(s"Unknow db type. driver = $driver") // Unknown
    }

  val isUnknownDbType: Boolean = dbType == Unknown

  override def toString: String =
    s"""~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       | DB type  : $dbType
       | driver   : $driver
       | url      : $url
       | username : $username
       | password : ******
       | ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       |""".stripMargin

}
