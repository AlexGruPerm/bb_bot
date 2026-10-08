package testsupport

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import zio.{ Task, ZIO }

import java.io.File
import java.nio.file.Paths
import java.sql.Connection

/**
 * Applies the production Liquibase changelog (`dock/migrations/master.xml`) to a raw JDBC connection.
 *
 * The changelog uses `<include relativeToChangelogFile="true" />`, so we point the resource accessor at the migrations
 * directory itself and pass `master.xml` as the changelog file.
 */
object LiquibaseTestRunner {

  private lazy val migrationsDir: File =
    // Walk up from the module working dir until dock/migrations is found (sbt runs each module in its own base dir).
    Iterator
      .iterate(Paths.get("").toAbsolutePath.toFile)(f => f.getParentFile)
      .takeWhile(_ != null)
      .map(f => new File(f, "dock/migrations"))
      .find(dir => new File(dir, "master.xml").isFile)
      .getOrElse(
        throw new IllegalStateException(
          "Cannot locate dock/migrations/master.xml from working dir " + Paths.get("").toAbsolutePath
        )
      )

  def run(connection: Connection): Task[Unit] =
    ZIO.attemptBlocking {
      val database  = DatabaseFactory
        .getInstance()
        .findCorrectDatabaseImplementation(new JdbcConnection(connection))
      val accessor  = new DirectoryResourceAccessor(migrationsDir.toPath)
      val liquibase = new Liquibase("master.xml", accessor, database)
      try liquibase.update(new Contexts(), new LabelExpression())
      finally liquibase.close()
    }

}
