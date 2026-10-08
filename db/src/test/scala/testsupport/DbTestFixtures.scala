package testsupport

import conf.DbConfig
import postgresql.PostgresqlService
import service.DatabaseService
import zio.{ ZIO, ZLayer }

import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

/**
 * Shared fixtures for DB-backed integration tests.
 *
 * [[migratedDataSource]] starts (once per JVM) the postgres test container and applies the production Liquibase
 * changelog exactly once. [[databaseService]] additionally wires the production [[DatabaseService]] on top of it.
 */
object DbTestFixtures {

  private val migrationsApplied = new AtomicBoolean(false)

  private def ensureMigrated: ZIO[DataSource, Throwable, Unit] =
    ZIO
      .when(!migrationsApplied.get()) {
        ZIO
          .acquireReleaseWith(
            ZIO.serviceWithZIO[DataSource](ds => ZIO.attemptBlocking(ds.getConnection))
          )(c => ZIO.attemptBlocking(c.close()).orDie) { conn =>
            LiquibaseTestRunner.run(conn) *> ZIO.succeed(migrationsApplied.set(true))
          }
          .unit
      }
      .unit

  /** Container + DbConfig + DataSource, with the full Liquibase changelog already applied. */
  val migratedDataSource: ZLayer[Any, Throwable, DbConfig with DataSource] =
    ZLayer.scopedEnvironment {
      PostgresTestContainer.live.build.flatMap { env =>
        ensureMigrated.provideEnvironment(env).as(env)
      }
    }

  /** Production DatabaseService wired to the migrated test datasource. */
  val databaseService: ZLayer[Any, Throwable, DbConfig with DataSource with DatabaseService] =
    migratedDataSource ++ ZLayer.succeed[DatabaseService](new PostgresqlService)

}
