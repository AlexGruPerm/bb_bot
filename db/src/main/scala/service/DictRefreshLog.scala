package service

import bybit_model.ErrorLog
import zio.{ ZEnvironment, ZIO }

import javax.sql.DataSource

object DictRefreshLog {

  private val module = "full refresh dict"

  /**
   * Logs a dictionary refresh into data.common_log: id_log_level = 1 (info), id_symbol = NULL, module = "full refresh
   * dict", action = dictionary name, error_class = "no error", msg = empty.
   */
  def log(dictName: String): ZIO[DatabaseService with DataSource, Nothing, Unit] =
    (for {
      db <- ZIO.service[DatabaseService]
      ds <- ZIO.service[DataSource]
      _  <- db
        .saveLogInDb(ErrorLog(1, module, dictName, "no error", ""))
        .provideEnvironment(ZEnvironment(ds))
    } yield ()).catchAll(err => ZIO.logError(s"Failed to write dict refresh log ($dictName): ${err.getMessage}"))
}
