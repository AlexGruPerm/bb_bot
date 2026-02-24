import bybit.ByBitService
import conf.AppConfig
import service.{
  AdvisorService,
  AskQueueService,
  CommunicationService,
  DatabaseService,
  ReglamentService,
  TelegramService,
  TraderService
}
import services.{ CoinService, SymbolsService }
import zio.ZIO

import java.sql.SQLException
import javax.sql.DataSource

package object app {
  type UserId = Long

  type TraderAppEnvs = AppConfig
    with DataSource
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

  type ZioDBsSQLExc     = ZIO[DatabaseService with DataSource, SQLException, Unit]
  type ZioDBsReglSQLExc = ZIO[DatabaseService with ReglamentService with DataSource, SQLException, Unit]

  type ZioTlgDBsAdv = TelegramService with DatabaseService with DataSource with AdvisorService

  type ZioTlgDBs = TelegramService with DatabaseService with DataSource

  type ByBitDsSymbol = ByBitService with DataSource with SymbolsService

}
