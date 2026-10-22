import bybit.ByBitService
import conf.AppConfig
import postgresql.DictChanges
import service.{
  AdvisorService,
  AskQueueService,
  CommunicationService,
  DatabaseService,
  FuturesMetricsScheduler,
  LlmAdviceScheduler,
  ReglamentService,
  TelegramService,
  TraderService,
  UsersService
}
import services.{ CoinService, SymbolsService }
import zio.ZIO
import zio.http.Client

import java.sql.SQLException
import javax.sql.DataSource

package object app {
  type UserId = Long

  type TraderAppEnvs = AppConfig
    with DataSource
    with Client
    with DatabaseService
    with ByBitService
    with TraderService
    with SymbolsService
    with CoinService
    with TelegramService
    with AskQueueService
    with CommunicationService
    with ReglamentService
    with AdvisorService
    with UsersService
    with DictChanges
    with FuturesMetricsScheduler
    with LlmAdviceScheduler

  type ZioDBsSQLExc     = ZIO[DatabaseService with DataSource, SQLException, Unit]
  type ZioDBsReglSQLExc = ZIO[DatabaseService with ReglamentService with DataSource, SQLException, Unit]

  type ZioTlgDBsAdv = TelegramService with DatabaseService with DataSource with AdvisorService

  type ZioTlgDBs = TelegramService with DatabaseService with DataSource

  type ByBitDsSymbol = ByBitService with DataSource with SymbolsService

}
