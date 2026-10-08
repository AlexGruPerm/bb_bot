package testsupport

import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import conf.DbConfig
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import zio.{ Runtime, Unsafe, ZIO, ZLayer }

import java.sql.SQLException
import javax.sql.DataSource

/**
 * Test-scoped PostgreSQL instance backed by a Docker container.
 *
 * The container is started once per JVM and reused by all test suites (see [[live]]). Migrations are applied by
 * [[LiquibaseTestRunner]] on top of the very same container.
 *
 * The datasource user points to the `data` schema owner so that Quill maps unqualified entities the same way the
 * application does.
 */
object PostgresTestContainer {

  val dbName: String     = "bybit_test"
  val dbUser: String     = "bb_user"
  val dbPassword: String = "bb_pwd"

  private final class TestPostgresContainer
      extends PostgreSQLContainer[TestPostgresContainer](DockerImageName.parse("postgres:17"))

  private val container: TestPostgresContainer =
    new TestPostgresContainer()
      .withDatabaseName(dbName)
      .withUsername(dbUser)
      .withPassword(dbPassword)
      // The app relies on the `data` schema; it is created by the Liquibase migration chain.
      .withInitScript("testcontainer-init.sql")

  private var started: TestPostgresContainer = _

  private def log(message: String): Unit =
    // The container is started lazily outside any ZIO effect, so we run a tiny logging effect eagerly here.
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(ZIO.logInfo(message)).getOrThrowFiberFailure()
    }

  private def containerRef: TestPostgresContainer =
    synchronized {
      if (started == null) {
        log(s"Starting test PostgreSQL container (image=postgres:17, db=$dbName)...")
        container.start()
        started = container
        log(
          s"Test PostgreSQL container is ready at ${started.getHost}:${started.getMappedPort(5432)} " +
            s"(db=$dbName, user=$dbUser)"
        )
      }
      started
    }

  val dbConfig: DbConfig =
    new DbConfig(
      driver = "org.postgresql.Driver",
      url = s"jdbc:postgresql://${containerRef.getHost}:${containerRef.getMappedPort(5432)}/$dbName",
      username = dbUser,
      password = dbPassword,
      maximumPoolSize = 4,
      minimumIdle = 1,
      connectionTimeout = 5000,
      poolName = "pg-pool-test",
      autoCommit = true
    )

  val dataSource: ZLayer[Any, SQLException, DataSource] =
    ZLayer.scoped {
      ZIO.acquireRelease(
        ZIO.attempt {
          val cfg = new HikariConfig()
          cfg.setJdbcUrl(dbConfig.url)
          cfg.setUsername(dbConfig.username)
          cfg.setPassword(dbConfig.password)
          cfg.setDriverClassName(dbConfig.driver)
          cfg.setMaximumPoolSize(dbConfig.maximumPoolSize)
          cfg.setMinimumIdle(dbConfig.minimumIdle)
          cfg.setConnectionTimeout(dbConfig.connectionTimeout)
          cfg.setPoolName(dbConfig.poolName)
          cfg.setAutoCommit(dbConfig.autoCommit)
          cfg.setConnectionInitSql("SET TIME ZONE 'UTC'")
          new HikariDataSource(cfg)
        }
          .refineToOrDie[SQLException]
      )(ds => ZIO.attempt(ds.close()).orDie)
    }

  val live: ZLayer[Any, SQLException, DbConfig with DataSource] =
    ZLayer.succeed(dbConfig) ++ dataSource

}
