package service

import bybit.{ ByBitService, KLineHandler }
import bybit_model.Types.{ IntervalCode, OrderID, SymbolCode }
import bybit_model._
import services.{ LogLevelService, LogLevelServiceImpl, PingPongService, SymbolsService }
import zio.http.Client
import zio.test._
import zio.{ Ref, Scope, ZIO, ZLayer }

import java.sql.SQLException
import javax.sql.DataSource

/**
 * Tests for the futures-position part of [[GatherService]] with in-memory stubs: Bybit returns a fixed payload and
 * DatabaseService records the inserts, so mapping / filtering can be checked without network or PostgreSQL.
 */
object GatherServicePositionsSpec extends ZIOSpecDefault {

  private val openPosition = PositionInfo(
    symbol = "BTCUSDT",
    side = "Buy",
    size = Some(BigDecimal(1)),
    avgPrice = Some(BigDecimal("100")),
    markPrice = Some(BigDecimal("101")),
    liqPrice = None,
    positionValue = Some(BigDecimal("100")),
    leverage = Some(BigDecimal(10)),
    positionIdx = Some(0),
    unrealisedPnl = Some(BigDecimal("1")),
    curRealisedPnl = Some(BigDecimal(0)),
    cumRealisedPnl = Some(BigDecimal(0)),
    takeProfit = None,
    stopLoss = None,
    updatedTime = Some(1700000000000L)
  )

  /** Bybit keeps a just-closed position in the list with size = 0, it must not be stored as an open one. */
  private val zeroSizePosition = openPosition.copy(symbol = "ETHUSDT", size = Some(BigDecimal(0)), side = "Sell")

  private val closedPnlItem = ClosedPnlItem(
    symbol = "ETHUSDT",
    side = "Sell",
    orderId = Some("order-1"),
    orderPrice = Some(BigDecimal("200")),
    orderType = Some("Market"),
    execType = Some("Trade"),
    closedSize = Some(BigDecimal(1)),
    cumEntryValue = Some(BigDecimal("200")),
    avgEntryPrice = Some(BigDecimal("200")),
    cumExitValue = Some(BigDecimal("190")),
    avgExitPrice = Some(BigDecimal("190")),
    closedPnl = Some(BigDecimal(10)),
    leverage = Some(BigDecimal(5)),
    updatedTime = Some(1700000060000L),
    createdTime = Some(1700000000000L)
  )

  /**
   * The stubs never touch the database: the DataSource only satisfies the effect's environment and must be non-null,
   * because ZLayer.succeed rejects null values.
   */
  private object UnusedDataSource extends DataSource {
    private def fail                                                                    = new UnsupportedOperationException("DataSource is not used in this spec")
    override def getConnection: java.sql.Connection                                     = throw fail
    override def getConnection(username: String, password: String): java.sql.Connection = throw fail
    override def getLogWriter: java.io.PrintWriter                                      = throw fail
    override def setLogWriter(out: java.io.PrintWriter): Unit                           = ()
    override def setLoginTimeout(seconds: Int): Unit                                    = ()
    override def getLoginTimeout: Int                                                   = 0
    override def getParentLogger: java.util.logging.Logger                              = java.util.logging.Logger.getGlobal
    override def unwrap[T](iface: Class[T]): T                                          = throw fail
    override def isWrapperFor(iface: Class[_]): Boolean                                 = false
  }

  private final case class Saved(open: List[FuturesPositionInsert], closed: List[FuturesPositionClosedInsert])

  private def stubByBit(positions: List[PositionInfo], closed: List[ClosedPnlItem]): ByBitService =
    new ByBitService {
      private val positionsResp = ApiRespPositions(
        retCode = 0,
        retMsg = "OK",
        result = PositionsResult("linear", positions, None),
        retExtInfo = None,
        time = None
      )

      private val closedResp = ApiRespClosedPnl(
        retCode = 0,
        retMsg = "OK",
        result = ClosedPnlResult("linear", closed, None),
        retExtInfo = None,
        time = None
      )

      override def getPositions(): ZIO[Scope with Client, Throwable, ApiRespPositions] =
        ZIO.succeed(positionsResp)

      override def getClosedPnl(
        startMs: Long,
        endMs: Long,
        maxPages: Int
      ): ZIO[Scope with Client, Throwable, ApiRespClosedPnl] = ZIO.succeed(closedResp)

      override def getOrderBook(symbol: String): ZIO[Scope with Client, Throwable, OrderBookResult]        =
        ZIO.dieMessage("not used")
      override def getOpenInterest(symbol: String): ZIO[Scope with Client, Throwable, OpenInterestResult]  =
        ZIO.dieMessage("not used")
      override def getAndSaveBars(
        topics: Set[KLineTopic],
        c_interval: IntervalCode
      ): ZIO[KLineHandler with SymbolsService with PingPongService, Throwable, Unit] = ZIO.dieMessage("not used")
      override def getWalletBalance(): ZIO[Scope with Client, Throwable, ApiRespWalletBalance]             =
        ZIO.dieMessage("not used")
      override def orderCreate(advice: TradeAdvice): ZIO[Scope with Client, Throwable, ApiRespCreateOrder] =
        ZIO.dieMessage("not used")
      override def getOrderHistInfo(id: OrderID): ZIO[Scope with Client, Throwable, ApiRespOrderHistInfo]  =
        ZIO.dieMessage("not used")
      override def getFuturesData(): ZIO[Scope with Client, Throwable, FuturesDataResult]                  =
        ZIO.dieMessage("not used")
    }

  private def stubDb(saved: Ref[Saved]): DatabaseService =
    new DatabaseService {
      private def idFor(code: SymbolCode): Int = code.hashCode.abs % 100000 + 1

      override def saveFuturesPositions(positions: List[FuturesPositionInsert]): ZIO[DataSource, SQLException, Unit] =
        saved.update(s => s.copy(open = s.open ++ positions)).unit

      override def saveFuturesPositionsClosed(
        items: List[FuturesPositionClosedInsert]
      ): ZIO[DataSource, SQLException, Unit] =
        saved.update(s => s.copy(closed = s.closed ++ items)).unit

      override def resolveSymbolFuturesId(code: String): ZIO[DataSource, SQLException, Int] =
        ZIO.succeed(idFor(code))

      override def getFuturesPositionsConfig: ZIO[DataSource, SQLException, Option[FuturesPositionsConfig]] =
        ZIO.none

      // The remaining methods are irrelevant for this spec.
      override def isAvailiable: ZIO[DataSource, SQLException, Boolean]                                             = ZIO.succeed(true)
      override def getSymbols: ZIO[DataSource, SQLException, Set[Symbol]]                                           = ZIO.succeed(Set.empty)
      override def getCoins: ZIO[DataSource, SQLException, Set[Coin]]                                               = ZIO.succeed(Set.empty)
      override def getLogLevels: ZIO[DataSource, SQLException, Set[LogLevel]]                                       = ZIO.succeed(Set.empty)
      override def refSymbolsIntervals(ids: Set[Int]): ZIO[DataSource, SQLException, Set[RefSymbolsIntervals]]      =
        ZIO.succeed(Set.empty)
      override def saveOrderBook(s: Symbol, r: OrderBookResult): ZIO[DataSource, SQLException, Unit]                = ZIO.unit
      override def saveOpenInterest(s: Symbol, r: OpenInterestResult): ZIO[DataSource, SQLException, Unit]          = ZIO.unit
      override def saveFuturesData(d: FuturesDataResult): ZIO[DataSource, SQLException, Unit]                       = ZIO.unit
      override def getFuturesMetrics: ZIO[DataSource, SQLException, List[FuturesMetrics]]                           = ZIO.succeed(Nil)
      override def getFuturesMetricsMeta: ZIO[DataSource, SQLException, List[FuturesMetricsMeta]]                   = ZIO.succeed(Nil)
      override def executeFuturesMetricsAnalysis(
        metric: FuturesMetrics,
        meta: FuturesMetricsMeta
      ): ZIO[DataSource, SQLException, Unit] = ZIO.unit
      override def getLlmPrompts: ZIO[DataSource, SQLException, List[LlmPrompt]]                                    = ZIO.succeed(Nil)
      override def getFuturesMetricsMetaById(id: Int): ZIO[DataSource, SQLException, Option[FuturesMetricsMeta]]    =
        ZIO.none
      override def getFuturesMetricsLlmSnapshot(
        metaId: Int
      ): ZIO[DataSource, SQLException, List[FuturesMetricsLlmRow]] =
        ZIO.succeed(Nil)
      override def insertLlmAdvice(
        promptId: Int,
        advice: String,
        adviceText: String
      ): ZIO[DataSource, SQLException, Int] =
        ZIO.succeed(0)
      override def insertLlmAdviceSourceResponse(
        llmAdviceId: Option[Int],
        request: String,
        response: String
      ): ZIO[DataSource, SQLException, Unit] = ZIO.unit
      override def markLlmAdviceSent(id: Int): ZIO[DataSource, SQLException, Unit]                                  = ZIO.unit
      override def getLlmAdviceText(id: Int): ZIO[DataSource, SQLException, Option[String]]                         = ZIO.none
      override def saveBar(
        kline: KLine,
        ref: Ref[Map[KLineTopic, CurrentCandle]],
        symbol: Symbol
      ): ZIO[DataSource, SQLException, Unit] = ZIO.unit
      override def saveWalletBalance(
        coins: Set[Coin],
        walletBalance: ApiRespWalletBalance
      ): ZIO[DataSource, SQLException, Unit] = ZIO.unit
      override def getMaxWalletBalanceId: ZIO[DataSource, SQLException, Long]                                       = ZIO.succeed(0L)
      override def getCommonWalletBalance(maxId: Long): ZIO[DataSource, SQLException, CommonWalletBalance]          =
        ZIO.dieMessage("not used")
      override def getSymbolsBalance(maxId: Long): ZIO[DataSource, SQLException, List[SymbolsBalance]]              =
        ZIO.succeed(Nil)
      override def saveOrderHistory(order: OrderItemInfoInsert): ZIO[DataSource, SQLException, Unit]                = ZIO.unit
      override def saveLogInDb(err: ErrorLog): ZIO[DataSource, SQLException, Unit]                                  = ZIO.unit
      override def getAdminAlerts: ZIO[DataSource, SQLException, List[AdminAlert]]                                  = ZIO.succeed(Nil)
      override def markAdminAlertsSent(ids: List[Long]): ZIO[DataSource, SQLException, Unit]                        = ZIO.unit
      override def executeReglamentCleanup: ZIO[DataSource, SQLException, Unit]                                     = ZIO.unit
      override def getAdviceIntervals: ZIO[DataSource, SQLException, List[bybit_model.Types.IntervalIntMins]]       =
        ZIO.succeed(Nil)
      override def getSymbolAdviceProcs(mins: Int): ZIO[DataSource, SQLException, List[SymbolAdviceProc]]           =
        ZIO.succeed(Nil)
      override def getAndSaveAdvice(
        sap: SymbolsAdviceProc
      ): ZIO[DataSource, SQLException, List[bybit_model.Types.AdviceId]] =
        ZIO.succeed(Nil)
      override def getAllAdvice(): ZIO[DataSource, SQLException, List[AdviceToUser]]                                = ZIO.succeed(Nil)
      override def getViewDeep(interval: String, deep_bars: Int): ZIO[DataSource, SQLException, List[ViewDeepLine]] =
        ZIO.succeed(Nil)
      override def getUsers: ZIO[DataSource, SQLException, List[DbUser]]                                            = ZIO.succeed(Nil)
      override def upsertUser(user: UsersInsert): ZIO[DataSource, SQLException, Unit]                               = ZIO.unit
      override def deactivateUser(tgUserId: Long, reason: String): ZIO[DataSource, SQLException, Unit]              = ZIO.unit
      override def saveTgCommandLog(idUser: Int, command: String): ZIO[DataSource, SQLException, Unit]              = ZIO.unit
    }

  private def logLevels: ZLayer[Any, Nothing, LogLevelService] =
    ZLayer.fromZIO(
      Ref
        .make(Set(LogLevel(1, "error")))
        .map(levels => LogLevelServiceImpl(levels): LogLevelService)
    )

  private def run(
    positions: List[PositionInfo],
    closed: List[ClosedPnlItem]
  )(
    effect: GatherService => ZIO[app.CommonGatherPosEnv, Throwable, Unit]
  ): ZIO[Any, Throwable, Saved] =
    for {
      ref <- Ref.make(Saved(Nil, Nil))
      gs   = new GatherServiceLive(stubDb(ref))
      _   <- effect(gs).provide(
        ZLayer.succeed[GatherService](gs),
        ZLayer.succeed(stubByBit(positions, closed)),
        ZLayer.succeed[DataSource](UnusedDataSource),
        logLevels
      )
      out <- ref.get
    } yield out

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("GatherServicePositionsSpec")(
      test("saveFuturesPositions stores only positions with size > 0") {
        run(List(openPosition, zeroSizePosition), Nil)(_.saveFuturesPositions()).map { saved =>
          assertTrue(
            saved.open.size == 1,
            saved.open.head.side == "Buy",
            saved.open.head.size.contains(BigDecimal(1)),
            saved.open.head.idSymbolFutures > 0
          )
        }
      },
      test("saveFuturesPositionsClosed maps closed-pnl fields and resolves the symbol id") {
        run(Nil, List(closedPnlItem))(_.saveFuturesPositionsClosed(120)).map { saved =>
          assertTrue(
            saved.closed.size == 1,
            saved.closed.head.side == "Sell",
            saved.closed.head.orderId.contains("order-1"),
            saved.closed.head.closedPnl.contains(BigDecimal(10)),
            saved.closed.head.updatedTime.contains(1700000060000L),
            saved.closed.head.createdTime.contains(1700000000000L),
            saved.closed.head.idSymbolFutures > 0
          )
        }
      },
      test("a record with an unknown side is skipped instead of failing the poll") {
        run(List(openPosition.copy(side = "Unknown")), Nil)(_.saveFuturesPositions()).map { saved =>
          assertTrue(saved.open.isEmpty)
        }
      }
    )
}
