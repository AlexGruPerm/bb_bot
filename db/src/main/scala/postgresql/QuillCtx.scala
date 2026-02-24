package postgresql

import io.getquill.{ PostgresZioJdbcContext, SnakeCase }

object QuillCtx {
  val ctx = new PostgresZioJdbcContext(SnakeCase)
}
