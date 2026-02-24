package app

import bybit.ByBitService
import bybit_model.CustDbException.UnknownDbException
import conf.AppConfig
import conf.ConfigLayer.configLayer
import conf.InputJsonConfig.getInputJsonFilePath
import service.{
  AdvisorService,
  AskQueueService,
  CommunicationService,
  DatabaseService,
  Datasource,
  ReglamentService,
  TelegramService,
  TraderService
}
import services.{ CoinService, SymbolsService }
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault }

object TradeBot extends ZIOAppDefault {

  private val MainApp: ZIO[TraderAppEnvs, Throwable, Unit] = for {
    _       <- ZIO.logInfo(s"Begin ByBit TradeBot.")
    _       <- ZIO.serviceWithZIO[AppConfig](conf => ZIO.logInfo(conf.toString))

    db      <- ZIO.service[DatabaseService]
    symbols <- db.getSymbols
    _       <- ZIO.serviceWithZIO[SymbolsService](_.addSymbols(symbols))
    coins   <- db.getCoins
    _       <- ZIO.serviceWithZIO[CoinService](_.addCoins(coins))

    _ <- ReglamentService.startReglamentCleanup
    _ <- ZIO.logInfo(s"Total symbols = [${symbols.size}] tradable = [${symbols.count(_.is_tradable)}]")

    adviceIntervals <- db.getAdviceIntervals
    _ <- AdvisorService.runAdvisorForIntervals(adviceIntervals)

    _ <- ZIO.serviceWithZIO[TelegramService](_.run())
    _ <- ZIO.serviceWithZIO[CommunicationService](_.runConsumer)

    /*
    _ <- ZIO.foreachParDiscard(symbols) { sym =>
      Trader
        .trade(sym)
        .repeat(Schedule.spaced(1.seconds))
        .fork
    }
    */
  } yield ()

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
        TelegramService.live,
        AskQueueService.live,
        CommunicationService.live,
        ReglamentService.live,
        AdvisorService.live
      )
      .catchSome {
        case err if err == UnknownDbException => ZIO.logError(s"Failed : ${err.getMessage}").unit
      }
  } yield res

}