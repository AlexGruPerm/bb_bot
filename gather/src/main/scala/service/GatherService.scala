package service

import app.{ ByBitDsCoins, ByBitDsSymbols, ByBitDsSymbolsPp }
import bybit.{ ByBitService, KLineHandler }
import bybit_model.Types.{ IntervalCode, IntervalId }
import bybit_model.{ CurrentCandle, ErrorLog, IntervalCodeId, KLineTopic, RefSymbolsIntervals }
import services.{ CoinService, PingPongService, SymbolsService, SymbolsServiceError }
import zio.http.Client
import zio.{ Ref, ZIO, ZLayer }

import java.sql.SQLException
import javax.sql.DataSource

trait GatherService {

  def saveOrderBook(): ZIO[ByBitDsSymbols, Throwable, Unit]
  def saveOpenInterest(): ZIO[ByBitDsSymbols, Throwable, Unit]
  def saveBars(optInterval: Option[IntervalCode] = None): ZIO[ByBitDsSymbolsPp, Throwable, Unit]
  def saveWalletBalance(): ZIO[ByBitDsCoins, Throwable, Unit]
  def saveErrorInDb(err: ErrorLog): ZIO[DataSource, SQLException, Unit]
  // def savePositionInfo(): ZIO[ByBitService with DataSource with SymbolsService, Throwable, Unit]
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

  override def saveWalletBalance(): ZIO[ByBitDsCoins, Throwable, Unit] = for {
    bbService     <- ZIO.service[ByBitService]
    coins         <- ZIO.serviceWithZIO[CoinService](_.getCoins())
    walletBalance <- ZIO.scoped(bbService.getWalletBalance()).provide(client)
    _             <- db.saveWalletBalance(coins, walletBalance)
  } yield ()

  /*
  override def savePositionInfo(): ZIO[ByBitService with DataSource with SymbolsService, Throwable, Unit] = for {
    bbService <- ZIO.service[ByBitService]
    symbols <- ZIO.serviceWithZIO[SymbolsService](_.getSymbols())
    _ <- ZIO.scoped(bbService.getPositionList(symbols)).provide(client)
  } yield ()
   */

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
