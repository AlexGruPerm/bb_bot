package app

import bybit.ByBitService
import bybit_model.CustDbException.UnknownDbException
import bybit_model.ErrorLog
import conf.AppConfig
import conf.ConfigLayer.configLayer
import conf.InputJsonConfig.getInputJsonFilePath
import postgresql.{ DictChanges, DictEvent }
import service.{ DatabaseService, Datasource, DictRefreshLog, GatherService }
import services.{ CoinService, LogLevelService, PingPongService, SymbolsService }
import zio._

import javax.sql.DataSource

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
    _         <- ZIO.foreachDiscard(coins)(c => ZIO.logInfo(c.code))
    _         <- ZIO.serviceWithZIO[CoinService](_.addCoins(coins))
    logLevels <- db.getLogLevels
    _         <- ZIO.serviceWithZIO[LogLevelService](_.add(logLevels))
  } yield ()

  private def refreshSymbols: ZIO[CommonGatherEnvConf, Nothing, Unit] =
    (for {
      db      <- ZIO.service[DatabaseService]
      ds      <- ZIO.service[DataSource]
      symbols <- db.getSymbols.provideEnvironment(ZEnvironment(ds))
      _       <- ZIO.serviceWithZIO[SymbolsService](_.replace(symbols))
      _       <- ZIO.logInfo(s"Dictionary 'symbol' refreshed on notification, count = ${symbols.size}")
      _       <- DictRefreshLog.log("symbol")
    } yield ()).catchAll(err => ZIO.logError(s"Failed to refresh dict symbol: ${err.getMessage}"))

  private def refreshCoins: ZIO[CommonGatherEnvConf, Nothing, Unit] =
    (for {
      db    <- ZIO.service[DatabaseService]
      ds    <- ZIO.service[DataSource]
      coins <- db.getCoins.provideEnvironment(ZEnvironment(ds))
      _     <- ZIO.serviceWithZIO[CoinService](_.replace(coins))
      _     <- ZIO.logInfo(s"Dictionary 'coin' refreshed on notification, count = ${coins.size}")
      _     <- DictRefreshLog.log("coin")
    } yield ()).catchAll(err => ZIO.logError(s"Failed to refresh dict coin: ${err.getMessage}"))

  /**
   * Subscribes to dictionary change notifications and replaces in-memory Refs. ref_symbol_interval is intentionally
   * ignored (no cache in gather).
   */
  private def startDictRefresh: ZIO[CommonGatherEnvConf, Nothing, Unit] =
    ZIO.serviceWithZIO[DictChanges] { dc =>
      dc.events.foreach {
        case DictEvent.TableChanged("symbol") => refreshSymbols
        case DictEvent.TableChanged("coin")   => refreshCoins
        case DictEvent.TableChanged(_)        => ZIO.unit
        case DictEvent.Resync                 => refreshSymbols *> refreshCoins
      }
    }

  private val MainApp: ZIO[CommonGatherEnvConf, Throwable, Unit] = for {
    conf <- ZIO.service[AppConfig]
    _    <- ZIO.logInfo(s"Begin ByBit gather.")
    _    <- ZIO.logInfo(conf.toString)
    _    <- ZIO.fail(UnknownDbException).when(conf.db.isUnknownDbType)

    _ <- initRefDictionaries
    _ <- startDictRefresh.fork

    _ <- Saver.saveOrderBooks(conf.bybitAccount.save_order_book_freq_mins)
    _ <- Saver.saveOpenInterests(conf.bybitAccount.save_oi_freq_mins)
    _ <- Saver.saveFuturesData(conf.bybitAccount.save_futures_data_freq_mins)

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
        LogLevelService.layer,
        DictChanges.live
      )
      .catchSome {
        case err if err == UnknownDbException => ZIO.logError(s"Failed : ${err.getMessage}").unit
      }
  } yield res

}
