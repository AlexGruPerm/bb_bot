package app

import bybit.ByBitService
import bybit_model.CustDbException.UnknownDbException
import bybit_model.ErrorLog
import conf.AppConfig
import conf.ConfigLayer.configLayer
import conf.InputJsonConfig.getInputJsonFilePath
import service.{ DatabaseService, Datasource, GatherService }
import services.{ CoinService, LogLevelService, PingPongService, SymbolsService }
import zio._

object Gather extends ZIOAppDefault {

  private def runPingPongChecker(
    duration: Duration,
    ppCheckInterval: Int
  ): URIO[PingPongService, Fiber.Runtime[Nothing, Long]] =
    ZIO
      .serviceWithZIO[PingPongService](_.checkAllIntervals(ppCheckInterval))
      .repeat(Schedule.spaced(duration))
      .fork

  private val checkRestartSaveBar: ZIO[CommonGatherPpEnv, Throwable, Unit] = for {
    restartIntervals <- ZIO.serviceWithZIO[PingPongService](_.getIntervalsForRestart)
    _                <- ZIO.when(restartIntervals.nonEmpty)(
      for {
        _ <- ZIO.logInfo(s"[ADD LOG TO DB] Sleep and START NEW saveBars(interval = $restartIntervals)")
        _ <- ZIO.foreachPar(restartIntervals) { interval =>
          for {
            logLevel <- ZIO.serviceWithZIO[LogLevelService](_.findByCode("error"))
            _        <- Saver.funcSaveLogDb(
              ErrorLog(
                logLevel.id,
                Saver.module,
                "checkRestartSaveBar",
                "PinPong error",
                s"Restart saveBars [int=$interval]"
              )
            )
            _        <- Saver.saveBars(onlyInterval = Some(interval))
          } yield ()
        }
      } yield ()
    )
  } yield ()

  private val initRefDictionaries: ZIO[CommonGatherEnvConf, Throwable, Unit] = for {
    db        <- ZIO.service[DatabaseService]
    symbols   <- db.getSymbols
    _         <- ZIO.serviceWithZIO[SymbolsService](_.addSymbols(symbols))
    coins     <- db.getCoins
    _         <- ZIO.serviceWithZIO[CoinService](_.addCoins(coins))
    logLevels <- db.getLogLevels
    _         <- ZIO.serviceWithZIO[LogLevelService](_.add(logLevels))
  } yield ()

  private val MainApp: ZIO[CommonGatherEnvConf, Throwable, Unit] = for {
    conf <- ZIO.service[AppConfig]
    _    <- ZIO.logInfo(s"Begin ByBit gather.")
    _    <- ZIO.logInfo(conf.toString)
    _    <- ZIO.fail(UnknownDbException).when(conf.db.isUnknownDbType)

    _ <- initRefDictionaries

    _ <- Saver.saveOrderBooks(conf.bybitAccount.save_order_book_freq_mins)
    _ <- Saver.saveOpenInterests(conf.bybitAccount.save_oi_freq_mins)

    _ <- runPingPongChecker(conf.bybitAccount.pp_check_freq_sec.seconds, conf.bybitAccount.pp_check_restart)
    _ <- checkRestartSaveBar.repeat(Schedule.spaced(conf.bybitAccount.check_restart_savebar_freq.seconds)).fork
    _ <- Saver.saveBars()

    _ <- Saver.saveWalletBalance
    _ <- ZIO.never
  } yield ()

  def run: ZIO[ZIOAppArgs with Scope, Any, Any] = for {
    jsonConfigPath <- getInputJsonFilePath
    res            <- MainApp
      .provide(
        configLayer(jsonConfigPath),
        DatabaseService.layer,
        GatherService.live,
        ByBitService.live,
        Datasource.live,
        SymbolsService.layer,
        CoinService.layer,
        PingPongService.layer,
        LogLevelService.layer
      )
      .catchSome {
        case err if err == UnknownDbException => ZIO.logError(s"Failed : ${err.getMessage}").unit
      }
  } yield res

}
