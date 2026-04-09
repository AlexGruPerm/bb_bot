package app

import bybit_model.ErrorLog
import bybit_model.Types.IntervalCode
import service.GatherService
import services.LogLevelService
import zio.{ durationInt, Schedule, ZIO }
import java.sql.SQLException

object Saver {

  val module: String = "Gather"

  implicit val funcSaveLogDb: ErrorLog => ZIO[GatherDataSourceEnv, SQLException, Unit] = err =>
    ZIO.serviceWithZIO[GatherService](_.saveErrorInDb(err))

  def saveOrderBooks(repeat_interval_mins: Int): ZIO[CommonGatherPpEnv, Throwable, Unit] = for {
    logLevelId <- ZIO.serviceWithZIO[LogLevelService](_.findByCode("error"))
    _          <- ZIO.serviceWithZIO[GatherService](
      _.saveOrderBook().catchSome {
        ErrorHandlers.logPF(logLevelId.id, module, "saveOrderBooks")
      }
        .repeat(Schedule.spaced(repeat_interval_mins.minutes))
        .fork
    )
  } yield ()

  def saveOpenInterests(repeat_interval_mins: Int): ZIO[CommonGatherPpEnv, Throwable, Unit] = for {
    logLevelId <- ZIO.serviceWithZIO[LogLevelService](_.findByCode("error"))
    _          <- ZIO.serviceWithZIO[GatherService](
      _.saveOpenInterest().catchSome {
        ErrorHandlers.logPF(logLevelId.id, module, "saveOpenInterests")
      }
        .repeat(Schedule.spaced(repeat_interval_mins.minutes))
        .fork
    )
  } yield ()

  def saveBars(onlyInterval: Option[IntervalCode] = None): ZIO[CommonGatherPpEnv, Throwable, Unit] = for {
    _ <- ZIO.serviceWithZIO[GatherService](_.saveBars(onlyInterval))
  } yield ()

  val saveWalletBalance: ZIO[CommonGatherCoinEnv, Throwable, Unit] = for {
    logLevelId <- ZIO.serviceWithZIO[LogLevelService](_.findByCode("error"))
    _          <- ZIO.serviceWithZIO[GatherService](gs =>
      gs.saveWalletBalance()
        .catchSome {
          ErrorHandlers.logPF(logLevelId.id, module, "saveWalletBalance")
        }
        .repeat(Schedule.spaced(5.seconds))
    )
  } yield ()

}
