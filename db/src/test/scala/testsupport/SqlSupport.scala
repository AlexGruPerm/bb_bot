package testsupport

import zio.{ Task, ZIO }

import javax.sql.DataSource
import java.sql.Connection

/** Small raw-JDBC helpers for seeding/cleaning data around integration tests. */
object SqlSupport {

  private def withConnection[A](f: Connection => A): ZIO[DataSource, Throwable, A] =
    ZIO.acquireReleaseWith(
      ZIO.serviceWithZIO[DataSource](ds => ZIO.attemptBlocking(ds.getConnection))
    )(c => ZIO.attemptBlocking(c.close()).orDie)(c => ZIO.attemptBlocking(f(c)))

  def execute(sql: String): ZIO[DataSource, Throwable, Unit] =
    withConnection { conn =>
      val st = conn.createStatement()
      try st.execute(sql)
      finally st.close()
    }.unit

  def queryLong(sql: String): ZIO[DataSource, Throwable, Long] =
    withConnection { conn =>
      val st = conn.createStatement()
      val rs = st.executeQuery(sql)
      try if (rs.next()) rs.getLong(1) else 0L
      finally {
        rs.close(); st.close()
      }
    }

  def queryString(sql: String): ZIO[DataSource, Throwable, Option[String]] =
    withConnection { conn =>
      val st = conn.createStatement()
      val rs = st.executeQuery(sql)
      try if (rs.next()) Option(rs.getString(1)) else None
      finally {
        rs.close(); st.close()
      }
    }

  def cleanup(sql: String): ZIO[DataSource, Nothing, Unit] =
    execute(sql).catchAll(e => ZIO.logError(s"cleanup failed: ${e.getMessage}"))

}
