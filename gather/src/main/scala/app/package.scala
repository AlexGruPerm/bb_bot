import bybit.{ ByBitService, KLineHandler }
import conf.AppConfig
import service.{ DatabaseService, GatherService }
import services.{ CoinService, LogLevelService, PingPongService, SymbolsService }
import zio.http.Client
import zio.{ Queue, Scope }

import javax.sql.DataSource

package object app {
  type CommonGatherEnvConf =
    AppConfig
      with DataSource
      with DatabaseService
      with GatherService
      with ByBitService
      with SymbolsService
      with CoinService
      with PingPongService
      with LogLevelService

  type CommonGatherCoinEnv =
    GatherService with ByBitService with DataSource with CoinService with LogLevelService

  type CommonGatherPpEnv =
    GatherService with ByBitService with DataSource with SymbolsService with PingPongService with LogLevelService

  type ByBitDsSymbols = ByBitService with DataSource with SymbolsService

  type ByBitDsSymbolsPp = ByBitService with DataSource with SymbolsService with PingPongService

  type ByBitDsCoins = ByBitService with DataSource with CoinService

  type CommonGatherEnv =
    GatherService with ByBitService with DataSource with SymbolsService

  type GatherDataSourceEnv = GatherService with DataSource

}
