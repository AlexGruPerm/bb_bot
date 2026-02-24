package service

import conf.{ AppConfig, Clickhouse, DbConfig, Oracle, Postgresql, Unknown }
import postgresql.PostgresDatasource
import zio.{ ZIO, ZLayer }

import javax.sql.DataSource

object Datasource {

  private val postgres: ZLayer[DbConfig, Throwable, DataSource] =
    PostgresDatasource.live

  private val oracle: ZLayer[DbConfig, Throwable, DataSource] =
    ZLayer.fail(new UnsupportedOperationException("Oracle DS not implemented"))

  private val clickhouse: ZLayer[DbConfig, Throwable, DataSource] =
    ZLayer.fail(new UnsupportedOperationException("ClickHouse DS not implemented"))

  // AppConfig -> DataSource
  val live: ZLayer[AppConfig, Throwable, DataSource] =
    ZLayer.fromZIO(ZIO.service[AppConfig]).flatMap { env =>
      val app = env.get
      val db  = app.db
      ZLayer.fromZIO(ZIO.logInfo(db.toString)) >>> {
        db.dbType match {
          case Postgresql => ZLayer.succeed(db) >>> postgres
          case Oracle     => ZLayer.succeed(db) >>> oracle
          case Clickhouse => ZLayer.succeed(db) >>> clickhouse
          case Unknown    => ZLayer.fail(new IllegalArgumentException(s"Unknown database type: ${db.driver}"))
        }
      }
    }
}
