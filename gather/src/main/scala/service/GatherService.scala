package service

import app.{ ByBitDsCoins, ByBitDsSymbols, ByBitDsSymbolsPp, CommonGatherPosEnv }
import bybit.{ ByBitService, KLineHandler }
import bybit_model.Types.{ IntervalCode, IntervalId }
import bybit_model.{
  ClosedPnlItem,
  CurrentCandle,
  ErrorLog,
  FuturesPositionClosedInsert,
  FuturesPositionInsert,
  IntervalCodeId,
  KLineTopic,
  PositionInfo,
  RefSymbolsIntervals
}
import services.{ CoinService, PingPongService, SymbolsService, SymbolsServiceError }
import zio.http.Client
import zio.{ Clock, Ref, ZIO, ZLayer }

import java.sql.SQLException
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

trait GatherService {

  def saveOrderBook(): ZIO[ByBitDsSymbols, Throwable, Unit]
  def saveOpenInterest(): ZIO[ByBitDsSymbols, Throwable, Unit]
  def saveBars(optInterval: Option[IntervalCode] = None): ZIO[ByBitDsSymbolsPp, Throwable, Unit]
  def saveWalletBalance(): ZIO[ByBitDsCoins, Throwable, Unit]
  def saveErrorInDb(err: ErrorLog): ZIO[DataSource, SQLException, Unit]
  def saveFuturesData(): ZIO[ByBitDsSymbols, Throwable, Unit]

  /** Stores one append-only snapshot of all currently open linear positions (only rows with size > 0). */
  def saveFuturesPositions(): ZIO[CommonGatherPosEnv, Throwable, Unit]

  /**
   * Stores closed positions of the last `lookbackMin` minutes as reported by Bybit.
   *
   * @param lookbackMin
   *   how far back to query /v5/position/closed-pnl
   */
  def saveFuturesPositionsClosed(lookbackMin: Int): ZIO[CommonGatherPosEnv, Throwable, Unit]
}

final class GatherServiceLive(db: DatabaseService) extends GatherService {

  private val client = Client.default

  /**
   * Get all enabled symbols from DB. Get OrderBook data from ByBit. Save OrderBook to db.
   */
  override def saveOrderBook(): ZIO[ByBitDsSymbols, Throwable, Unit] = for {
    bbService <- ZIO.service[ByBitService]
    symbols   <- ZIO.serviceWithZIO[SymbolsService](_.getSymbols())
    _         <- ZIO.foreach(symbols) { s =>
      ZIO.scoped(bbService.getOrderBook(s.code)).provide(client).flatMap(db.saveOrderBook(s, _))
    }
  } yield ()

  override def saveOpenInterest(): ZIO[ByBitDsSymbols, Throwable, Unit] = for {
    bbService <- ZIO.service[ByBitService]
    symbols   <- ZIO.serviceWithZIO[SymbolsService](_.getSymbols())
    _         <- ZIO.foreach(symbols) { s =>
      ZIO.scoped(bbService.getOpenInterest(s.code)).provide(client).flatMap(db.saveOpenInterest(s, _))
    }
  } yield ()

  override def saveFuturesData(): ZIO[ByBitDsSymbols, Throwable, Unit] = for {
    bbService <- ZIO.service[ByBitService]
    _         <- ZIO.scoped(bbService.getFuturesData()).provide(client).flatMap(db.saveFuturesData)
  } yield ()

  private def getKLineTopicsForInterval(
    refs: Set[RefSymbolsIntervals],
    interval: IntervalCodeId,
    ss: SymbolsService
  ): ZIO[Any, SymbolsServiceError, Set[KLineTopic]] = {
    val requests: Set[ZIO[Any, SymbolsServiceError, KLineTopic]] = refs
      .filter(_.id_interval == interval.id_interval)
      .map(r => ss.findSymbolById(r.id_symbol).map(s => KLineTopic.parse(r.kline_topic, s)))
    ZIO.collectAll(requests)
  }

  private def makeRefCandle(klineTopics: Set[KLineTopic]): ZIO[Any, Nothing, Ref[Map[KLineTopic, CurrentCandle]]] =
    Ref.make(
      Map.from(
        klineTopics.map(t => t -> CurrentCandle(interval = t.intervalCode))
      )
    )

  override def saveBars(optInterval: Option[IntervalCode] = None): ZIO[ByBitDsSymbolsPp, Throwable, Unit] = for {
    pps       <- ZIO.service[PingPongService]
    bbService <- ZIO.service[ByBitService]
    ss        <- ZIO.service[SymbolsService]
    symbols   <- ss.getSymbols()
    refs      <- db.refSymbolsIntervals(symbols.map(_.id))
    intervals  = optInterval
      .map(oi => refs.filter(_.c_interval == oi).map(i => IntervalCodeId(i.id_interval, i.c_interval)))
      .getOrElse(refs.map(ii => IntervalCodeId(ii.id_interval, ii.c_interval)))
    _         <- ZIO.foreachPar(intervals) { interval =>
      for {
        klineTopics   <- getKLineTopicsForInterval(refs, interval, ss)
        refCurrCandle <- makeRefCandle(klineTopics)
        fiber         <- bbService
          .getAndSaveBars(klineTopics, interval.c_interval)
          .provideSomeLayer[ByBitDsSymbolsPp](
            // Translate DataSource from environment into KLineHandler
            ZLayer.fromFunction((ds: DataSource) => ds) >>> KLineHandler.live { (kline, symbolId) =>
              db.saveBar(kline, refCurrCandle, symbolId)
            }
          )
          .tapError(e => ZIO.logError(e.getMessage))
          .fork
        _             <- pps.initEmptyPingPong(c_interval = interval.c_interval, fiber = fiber)
      } yield ()
    }
  } yield ()

  private def notFoundCoinMsg(coin: String) =
    s"Coin from ByBit ${coin} not found in DB data.coin, add it or update in dictionary"

  override def saveWalletBalance(): ZIO[ByBitDsCoins, Throwable, Unit] = for {
    bbService     <- ZIO.service[ByBitService]
    coins         <- ZIO.serviceWithZIO[CoinService](_.getCoins())
    walletBalance <- ZIO.scoped(bbService.getWalletBalance()).provide(client)
    _             <- ZIO.foreachDiscard(
      walletBalance.result.balance.toList.flatMap(_.coin).filterNot(c => coins.exists(_.code == c.coin))
    ) { c =>
      ZIO.logError(notFoundCoinMsg(c.coin)) *>
        saveErrorInDb(
          ErrorLog(
            id_loglevel = 3,
            bb_module = "PostgresqlService",
            bb_action = "saveWalletBalance",
            error_class = "SQLException",
            msg = notFoundCoinMsg(c.coin),
            for_admin = true
          )
        )
    }
    _             <- db.saveWalletBalance(coins, walletBalance)
  } yield ()

  /**
   * Positions with size = 0 are not open (Bybit keeps them in the list right after closing), so they are skipped.
   * Symbols unknown to data.symbol_futures are resolved (and inserted) before the snapshot is written.
   */
  override def saveFuturesPositions(): ZIO[CommonGatherPosEnv, Throwable, Unit] = for {
    bbService <- ZIO.service[ByBitService]
    positions <- ZIO.scoped(bbService.getPositions()).provide(client)
    open       = positions.result.list.filter(p => p.size.exists(_ > 0))
    rows      <- ZIO.foreach(open)(toPositionInsert)
    _         <- db.saveFuturesPositions(rows.flatten)
  } yield ()

  /** Skips a row with a side the DB check constraint would reject, so one bad record cannot fail the whole poll. */
  private def toPositionInsert(position: PositionInfo): ZIO[DataSource, Throwable, Option[FuturesPositionInsert]] =
    if (!validSide(position.side))
      ZIO.logWarning(s"futures_position: skip ${position.symbol} with unknown side '${position.side}'").as(None)
    else
      db.resolveSymbolFuturesId(position.symbol).map { idSymbol =>
        Some(
          FuturesPositionInsert(
            idSymbolFutures = idSymbol,
            side = position.side,
            size = position.size,
            avgPrice = position.avgPrice,
            markPrice = position.markPrice,
            liqPrice = position.liqPrice,
            positionValue = position.positionValue,
            leverage = position.leverage,
            positionIdx = position.positionIdx,
            unrealisedPnl = position.unrealisedPnl,
            curRealisedPnl = position.curRealisedPnl,
            cumRealisedPnl = position.cumRealisedPnl,
            takeProfit = position.takeProfit,
            stopLoss = position.stopLoss
          )
        )
      }

  private def toClosedInsert(item: ClosedPnlItem): ZIO[DataSource, Throwable, Option[FuturesPositionClosedInsert]] =
    if (!validSide(item.side))
      ZIO.logWarning(s"futures_position_closed: skip ${item.symbol} with unknown side '${item.side}'").as(None)
    else
      db.resolveSymbolFuturesId(item.symbol).map { idSymbol =>
        Some(
          FuturesPositionClosedInsert(
            idSymbolFutures = idSymbol,
            side = item.side,
            orderId = item.orderId,
            orderPrice = item.orderPrice,
            orderType = item.orderType,
            execType = item.execType,
            closedSize = item.closedSize,
            cumEntryValue = item.cumEntryValue,
            avgEntryPrice = item.avgEntryPrice,
            cumExitValue = item.cumExitValue,
            avgExitPrice = item.avgExitPrice,
            closedPnl = item.closedPnl,
            leverage = item.leverage,
            createdTime = item.createdTime,
            updatedTime = item.updatedTime
          )
        )
      }

  private def validSide(side: String): Boolean = side == "Buy" || side == "Sell"

  override def saveFuturesPositionsClosed(lookbackMin: Int): ZIO[CommonGatherPosEnv, Throwable, Unit] = for {
    bbService <- ZIO.service[ByBitService]
    nowMs     <- Clock.currentTime(TimeUnit.MILLISECONDS)
    closed    <- ZIO
      .scoped(bbService.getClosedPnl(nowMs - lookbackMin.toLong * 60_000L, nowMs))
      .provide(client)
    rows      <- ZIO.foreach(closed.result.list)(toClosedInsert)
    _         <- db.saveFuturesPositionsClosed(rows.flatten)
  } yield ()

  override def saveErrorInDb(err: ErrorLog): ZIO[DataSource, SQLException, Unit] =
    for {
      isAvail <- db.isAvailiable
      _       <-
        if (isAvail)
          db.saveLogInDb(err)
        else
          ZIO.logError("Database unavailable")
    } yield ()

}

object GatherService {
  val live: ZLayer[DatabaseService, Nothing, GatherService] =
    ZLayer {
      for {
        db <- ZIO.service[DatabaseService]
      } yield new GatherServiceLive(db)
    }
}
