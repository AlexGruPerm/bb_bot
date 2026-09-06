package service

import bybit_model.Types.{AdviceId, IntervalIntMins, SymbolId}
import bybit_model.{AdviceToUser, ApiRespWalletBalance, Coin, CommonWalletBalance, CurrentCandle, ErrorLog, FuturesDataResult, KLine, KLineTopic, LogLevel, OpenInterestResult, OrderBookResult, OrderItemInfoInsert, RefSymbolsIntervals, Symbol, SymbolAdviceProc, SymbolsAdviceProc, SymbolsBalance, TradeAdviceOrder, TradeAdviceSelect, ViewDeepLine}
import conf.{Postgresql, _}
import postgresql.{PostgresDatasource, PostgresqlService}
import zio.{Ref, ZIO, ZLayer}

import java.sql.SQLException
import javax.sql.DataSource

trait DatabaseService {
  def isAvailiable: ZIO[DataSource, SQLException, Boolean]
  def getSymbols: ZIO[DataSource, SQLException, Set[Symbol]]
  def getCoins: ZIO[DataSource, SQLException, Set[Coin]]
  def getLogLevels: ZIO[DataSource, SQLException, Set[LogLevel]]
  def refSymbolsIntervals(symbols: Set[SymbolId]): ZIO[DataSource, SQLException, Set[RefSymbolsIntervals]]
  def saveOrderBook(symbol: Symbol, orderBook: OrderBookResult): ZIO[DataSource, SQLException, Unit]
  def saveOpenInterest(symbol: Symbol, orderBook: OpenInterestResult): ZIO[DataSource, SQLException, Unit]
  def saveFuturesData(futuresData: FuturesDataResult): ZIO[DataSource, SQLException, Unit]
  def saveBar(
    kline: KLine,
    ref: Ref[Map[KLineTopic, CurrentCandle]],
    symbol: Symbol
  ): ZIO[DataSource, SQLException, Unit]
  def saveWalletBalance(coins: Set[Coin], walletBalance: ApiRespWalletBalance): ZIO[DataSource, SQLException, Unit]
  def getMaxWalletBalanceId: ZIO[DataSource, SQLException, Long]
  def getCommonWalletBalance(maxWalletBalanceId: Long): ZIO[DataSource, SQLException, CommonWalletBalance]
  def getSymbolsBalance(maxWalletBalanceId: Long): ZIO[DataSource, SQLException, List[SymbolsBalance]]
  // def getTradeAdvice(symbol: Symbol): ZIO[DataSource, SQLException, Option[TradeAdviceSelect]]
  // def saveTradeAdviceOrder(order: TradeAdviceOrder): ZIO[DataSource, SQLException, Unit]
  def saveOrderHistory(order: OrderItemInfoInsert): ZIO[DataSource, SQLException, Unit]
  def saveLogInDb(err: ErrorLog): ZIO[DataSource, SQLException, Unit]
  def executeReglamentCleanup(code: String): ZIO[DataSource, SQLException, Unit]
  def getAdviceIntervals: ZIO[DataSource, SQLException, List[IntervalIntMins]]
  def getSymbolAdviceProcs(mins: Int): ZIO[DataSource, SQLException, List[SymbolAdviceProc]]
  def getAndSaveAdvice(sap: SymbolsAdviceProc): ZIO[DataSource, SQLException, List[AdviceId]]
  def getAllAdvice(): ZIO[DataSource, SQLException, List[AdviceToUser]]
  def getViewDeep(interval: String, deep_bars: Int): ZIO[DataSource, SQLException, List[ViewDeepLine]]
}

object DatabaseService {

  private val postgresLive: ZLayer[DataSource, SQLException, DatabaseService] =
    ZLayer.succeed(new PostgresqlService)

  val layer: ZLayer[AppConfig, SQLException, DatabaseService] =
    ZLayer.service[AppConfig].flatMap { env =>
      env.get.db.dbType match {
        case Postgresql =>
          (ZLayer.succeed(env.get.db) >>> PostgresDatasource.live) >>> postgresLive
        // case Oracle => ZLayer.fail(new SQLException("Oracle not implemented"))
        // case Clickhouse => ZLayer.fail(new SQLException("ClickHouse not implemented"))
        case Unknown    =>
          ZLayer.fail(new SQLException(s"Unknown database type (from driver): ${env.get.db.driver}"))
      }
    }

}
