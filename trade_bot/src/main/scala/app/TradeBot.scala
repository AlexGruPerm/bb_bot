package app

import bybit.ByBitService
import bybit_model.AdminAlert
import bybit_model.CustDbException.UnknownDbException
import conf.AppConfig
import conf.ConfigLayer.configLayer
import conf.InputJsonConfig.getInputJsonFilePath
import model.SendAdminErrorLog
import postgresql.{ DictChanges, DictEvent }
import service.{
  AdvisorService,
  AskQueueService,
  CommunicationService,
  DatabaseService,
  Datasource,
  DictRefreshLog,
  ReglamentService,
  TelegramService,
  TraderService,
  UsersService
}
import services.{ CoinService, SymbolsService }
import zio.{ Scope, ZEnvironment, ZIO, ZIOAppArgs, ZIOAppDefault }

import javax.sql.DataSource

object TradeBot extends ZIOAppDefault {

  private val MainApp: ZIO[TraderAppEnvs, Throwable, Unit] = for {
    _ <- ZIO.logInfo(s"Begin ByBit TradeBot.")
    _ <- ZIO.serviceWithZIO[AppConfig](conf => ZIO.logInfo(conf.toString))

    db      <- ZIO.service[DatabaseService]
    symbols <- db.getSymbols
    _       <- ZIO.serviceWithZIO[SymbolsService](_.addSymbols(symbols))
    coins   <- db.getCoins
    _       <- ZIO.serviceWithZIO[CoinService](_.addCoins(coins))

    _ <- startDictRefresh.fork
    _ <- ReglamentService.startReglamentCleanup
    _ <- ZIO.logInfo(s"Total symbols = [${symbols.size}] tradable = [${symbols.count(_.is_tradable)}]")

    adviceIntervals <- db.getAdviceIntervals
    _               <- AdvisorService.runAdvisorForIntervals(adviceIntervals)

    _ <- ZIO.serviceWithZIO[TelegramService](_.run())
    _ <- ZIO.serviceWithZIO[CommunicationService](_.runConsumer)

  } yield ()

  private final def refreshSymbols: ZIO[TraderAppEnvs, Nothing, Unit] =
    (for {
      db      <- ZIO.service[DatabaseService]
      ds      <- ZIO.service[DataSource]
      symbols <- db.getSymbols.provideEnvironment(ZEnvironment(ds))
      _       <- ZIO.serviceWithZIO[SymbolsService](_.replace(symbols))
      _       <- ZIO.logInfo(s"Dictionary 'symbol' refreshed on notification, count = ${symbols.size}")
      _       <- DictRefreshLog.log("symbol")
    } yield ()).catchAll(err => ZIO.logError(s"Failed to refresh dict symbol: ${err.getMessage}"))

  private final def refreshCoins: ZIO[TraderAppEnvs, Nothing, Unit] =
    (for {
      db    <- ZIO.service[DatabaseService]
      ds    <- ZIO.service[DataSource]
      coins <- db.getCoins.provideEnvironment(ZEnvironment(ds))
      _     <- ZIO.serviceWithZIO[CoinService](_.replace(coins))
      _     <- ZIO.logInfo(s"Dictionary 'coin' refreshed on notification, count = ${coins.size}")
      _     <- DictRefreshLog.log("coin")
    } yield ()).catchAll(err => ZIO.logError(s"Failed to refresh dict coin: ${err.getMessage}"))

  private final def refreshUsers: ZIO[TraderAppEnvs, Nothing, Unit] =
    ZIO.serviceWithZIO[UsersService](_.refreshUsers) *> DictRefreshLog.log("users")

  private def buildAdminAlertMessage(alerts: List[AdminAlert]): zio.UIO[String] =
    ZIO.succeed {
      s"""<pre>id      age          module.action          msg
         |${alerts.map { a =>
          s"${a.id}   ${a.readable_diff.padTo(11, ' ')}   ${a.module_action.padTo(22, ' ')}   ${a.msg}"
        }.mkString("\n")}
         |</pre>""".stripMargin
    }

  /**
   * On data.common_log change, collects unsent admin alerts and pushes them to the Ask queue as a single message, then
   * marks them as sent. The own UPDATE (marking as sent) fires a notification too, but the following SELECT returns no
   * rows, so no recursion.
   */
  private final def checkAdminAlerts: ZIO[TraderAppEnvs, Nothing, Unit] =
    (for {
      db     <- ZIO.service[DatabaseService]
      ds     <- ZIO.service[DataSource]
      q      <- ZIO.service[AskQueueService]
      alerts <- db.getAdminAlerts.provideEnvironment(ZEnvironment(ds))
      _      <- ZIO.when(alerts.nonEmpty) {
        for {
          msg <- buildAdminAlertMessage(alerts)
          _   <- q.askQ.offer(SendAdminErrorLog(msg))
          _   <- db.markAdminAlertsSent(alerts.map(_.id)).provideEnvironment(ZEnvironment(ds))
        } yield ()
      }
    } yield ()).catchAll(err => ZIO.logError(s"Failed to send admin alert: ${err.getMessage}"))

  /**
   * Subscribes to dictionary change notifications and replaces in-memory Refs. ref_symbol_interval is intentionally
   * ignored (no cache in trade_bot). data.common_log changes trigger admin alert sending.
   */
  private def startDictRefresh: ZIO[TraderAppEnvs, Nothing, Unit] =
    ZIO.serviceWithZIO[DictChanges] { dc =>
      dc.events.foreach {
        case DictEvent.TableChanged("symbol")     => refreshSymbols
        case DictEvent.TableChanged("coin")       => refreshCoins
        case DictEvent.TableChanged("users")      => refreshUsers
        case DictEvent.TableChanged("common_log") => checkAdminAlerts
        case DictEvent.TableChanged(_)            => ZIO.unit
        case DictEvent.Resync                     => refreshSymbols *> refreshCoins *> refreshUsers *> checkAdminAlerts
      }
    }

  def run: ZIO[ZIOAppArgs with Scope, Any, Any] = for {
    jsonConfigPath <- getInputJsonFilePath
    res            <- MainApp
      .provide(
        configLayer(jsonConfigPath),
        DatabaseService.layer,
        ByBitService.live,
        Datasource.live,
        SymbolsService.layer,
        CoinService.layer,
        TraderService.live,
        UsersService.live,
        TelegramService.live,
        AskQueueService.live,
        CommunicationService.live,
        ReglamentService.live,
        AdvisorService.live,
        DictChanges.live
      )
      .catchSome {
        case err if err == UnknownDbException => ZIO.logError(s"Failed : ${err.getMessage}").unit
      }
  } yield res

}
