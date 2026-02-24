package postgresql

import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import conf.DbConfig
import zio.{ ZIO, ZLayer }

import java.sql.SQLException
import javax.sql.DataSource

object PostgresDatasource {

  val live: ZLayer[DbConfig, SQLException, DataSource] =
    ZLayer.scoped {
      for {
        db <- ZIO.service[DbConfig]
        ds <- ZIO.acquireRelease(
          ZIO.attempt {
            val cfg = new HikariConfig()
            cfg.setJdbcUrl(db.url)
            cfg.setUsername(db.username)
            cfg.setPassword(db.password)
            cfg.setDriverClassName(db.driver)
            cfg.setMaximumPoolSize(db.maximumPoolSize)
            cfg.setMinimumIdle(db.minimumIdle)
            cfg.setConnectionTimeout(db.connectionTimeout)
            cfg.setPoolName(db.poolName)
            cfg.setAutoCommit(db.autoCommit)
            cfg.setConnectionInitSql("SET TIME ZONE 'UTC'")
            new HikariDataSource(cfg)
          }.tap(ds =>
            ZIO.logInfo(
              s"HikariCP created: pool='${ds.getPoolName}', max=${ds.getMaximumPoolSize}, minIdle=${ds.getMinimumIdle}, autoCommit=${ds.isAutoCommit}"
            )
          ).refineToOrDie[SQLException]
        )(ds => ZIO.attempt(ds.close()).orDie)
      } yield ds
    }

}
