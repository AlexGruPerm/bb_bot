package postgresql

import bybit_model.Types.{AdviceId, IntervalIntMins, SymbolId}
import service.DatabaseService
import bybit_model.{Advice, AdviceInsert, AdviceMeta, AdviceToUser, ApiRespWalletBalance, CandleInsert, Coin, CommonWalletBalance, CurrentCandle, ErrorLog, FuturesDataResult, FuturesDataRow, InsertedCandle, Interval, KLine, KLineInsert, KLineTopic, LogLevel, OpenInterestInsert, OpenInterestResConverter, OpenInterestResult, OrderBookResConverter, OrderBookResult, OrderBookResultConverter, OrderBookResultInsert, OrderBookSnapshot, OrderBookSnapshotInsert, OrderItemInfo, OrderItemInfoInsert, RefAdviceMetaInterval, RefSymbolsIntervals, ReglamentLog, ReglamentRow, Symbol, SymbolAdviceProc, SymbolFutures, SymbolShort, SymbolSource, SymbolsAdviceProc, SymbolsBalance, TradeAdvice, TradeAdviceOrder, TradeAdviceSelect, TradeAdviceUpdate, ViewDeepLine, WalletBalanceCoinInsert, WalletBalanceInsert}
import io.getquill.{Delete, EntityQuery, Insert, Ord, Query, Quoted, Update}
import zio.{Ref, ZIO, durationInt}
import zio._

import java.sql.{SQLException, Timestamp}
import java.time.Instant
import java.util.concurrent.TimeoutException
import javax.sql.DataSource
import io.getquill.util.ContextLogger
import postgresql.QuillCtx.ctx

import java.time.format.DateTimeFormatter

final class PostgresqlService extends DatabaseService {

  implicit val logger: ContextLogger = new ContextLogger("quill")

  private val dbType: String = "PG"

  import QuillCtx.ctx._

  private val symbolSourceSchema = quote {
    querySchema[SymbolSource]("data.symbol")
  }

  private val symbolSchema = quote {
    querySchema[Symbol]("data.v_symbols")
  }

  private val symbolFutures = quote {
    querySchema[SymbolFutures]("data.symbol_futures")
  }

  private val symbolShort = quote {
    querySchema[SymbolShort]("data.symbol")
  }

  private val intervalSchema = quote {
    querySchema[Interval]("data.interval")
  }

  private val adviceMetaSchema = quote {
    querySchema[AdviceMeta]("data.advice_meta")
  }

  private val refAdviceMetaIntervalSchema = quote {
    querySchema[RefAdviceMetaInterval]("data.ref_advice_meta_interval")
  }

  private val coinSchema = quote {
    querySchema[Coin]("data.coin")
  }

  private val LogLevelSchema = quote {
    querySchema[LogLevel]("data.log_level")
  }

  private val refSymbolsIntervalsSchema = quote {
    querySchema[RefSymbolsIntervals]("data.ref_symbols_intervals")
  }

  private val FuturesDataRowSchema = quote {
    querySchema[FuturesDataRow](
      "data.futures_data",
      _.idSymbol              -> "id_symbol",
      _.lastPrice             -> "last_price",
      _.indexPrice            -> "index_price",
      _.markPrice             -> "mark_price",
      _.prevPrice24h          -> "prev_price_24h",
      _.price24hPcnt          -> "price_24h_pcnt",
      _.highPrice24h          -> "high_price_24h",
      _.lowPrice24h           -> "low_price_24h",
      _.prevPrice1h           -> "prev_price_1h",
      _.openInterest          -> "open_interest",
      _.openInterestValue     -> "open_interest_value",
      _.turnover24h           -> "turnover_24h",
      _.volume24h             -> "volume_24h",
      _.fundingRate           -> "funding_rate",
      _.nextFundingTime       -> "next_funding_time",
      _.ask1Size              -> "ask1_size",
      _.bid1Price             -> "bid1_price",
      _.ask1Price             -> "ask1_price",
      _.bid1Size              -> "bid1_size",
      _.fundingIntervalHour   -> "funding_interval_hour",
      _.fundingCap            -> "funding_cap"
    )
  }

  private val FuturesDataObservedRowSchema = quote {
    querySchema[FuturesDataRow](
      "data.futures_data_observed",
      _.idSymbol              -> "id_symbol",
      _.lastPrice             -> "last_price",
      _.indexPrice            -> "index_price",
      _.markPrice             -> "mark_price",
      _.prevPrice24h          -> "prev_price_24h",
      _.price24hPcnt          -> "price_24h_pcnt",
      _.highPrice24h          -> "high_price_24h",
      _.lowPrice24h           -> "low_price_24h",
      _.prevPrice1h           -> "prev_price_1h",
      _.openInterest          -> "open_interest",
      _.openInterestValue     -> "open_interest_value",
      _.turnover24h           -> "turnover_24h",
      _.volume24h             -> "volume_24h",
      _.fundingRate           -> "funding_rate",
      _.nextFundingTime       -> "next_funding_time",
      _.ask1Size              -> "ask1_size",
      _.bid1Price             -> "bid1_price",
      _.ask1Price             -> "ask1_price",
      _.bid1Size              -> "bid1_size",
      _.fundingIntervalHour   -> "funding_interval_hour",
      _.fundingCap            -> "funding_cap"
    )
  }

  private val orderBookSnapshotSchema = quote {
    querySchema[OrderBookSnapshot](
      "data.order_book_snapshot",
      _.id        -> "id",
      _.id_symbol -> "id_symbol",
      _.ts_db     -> "ts_db",
      _.ts_bybit  -> "ts_bybit"
    )
  }

  private val orderBookResultInsertSchema = quote {
    querySchema[OrderBookResultInsert](
      "data.order_book",
      _.id_snapshot -> "id_snapshot",
      _.side        -> "side",
      _.price       -> "price",
      _.amount      -> "amount"
    )
  }

  private val openInterestInsertSchema = quote {
    querySchema[OpenInterestInsert](
      "data.open_interest",
      _.id_symbol -> "id_symbol",
      _.ts_bybit  -> "ts_bybit",
      _.oi        -> "oi"
    )
  }

  private val tradeAdviceSchema = quote {
    querySchema[TradeAdviceSelect](
      "data.v_trade_advice",
      _.trade_advice_id       -> "trade_advice_id",
      _.id_symbol             -> "id_symbol",
      _.category              -> "category",
      _.symbol                -> "symbol",
      _.isLeverage            -> "isleverage",
      _.side                  -> "side",
      _.orderType             -> "ordertype",
      _.qty                   -> "qty",
      _.marketUnit            -> "marketunit",
      _.slippageToleranceType -> "slippagetolerancetype",
      _.slippageTolerance     -> "slippagetolerance"
    )
  }

  private val tradeAdviceUpdateSchema = quote {
    querySchema[TradeAdviceUpdate](
      "data.trade_advice",
      _.id       -> "id",
      _.is_taken -> "is_taken",
      _.taken_ts -> "taken_ts"
    )
  }

  private val TradeAdviceOrderSchema = quote {
    querySchema[TradeAdviceOrder](
      "data.trade_advice_order",
      _.orderId         -> "orderId",
      _.id_trade_advice -> "id_trade_advice",
      _.ts_bybit        -> "ts_bybit"
    )
  }

  private val CandleSchema = quote {
    querySchema[CandleInsert](
      "data.candle",
      _.id         -> "id",
      _.id_symbol  -> "id_symbol",
      _.start_ts   -> "start_ts",
      _.end_ts     -> "end_ts",
      _.c_interval -> "c_interval",
      _.o          -> "o",
      _.h          -> "h",
      _.l          -> "l",
      _.c          -> "c",
      _.v          -> "v"
    )
  }

  private val KLineSchema = quote {
    querySchema[KLineInsert](
      "data.kline",
      _.id_candle -> "id_candle",
      _.start_ts  -> "start_ts",
      _.end_ts    -> "end_ts",
      _.o         -> "o",
      _.h         -> "h",
      _.l         -> "l",
      _.c         -> "c",
      _.v         -> "v"
    )
  }

  private val WalletBalanceSchema = quote {
    querySchema[WalletBalanceInsert](
      "data.wallet_balance",
      _.id                    -> "id",
      _.totalEquity           -> "totalEquity",
      _.totalInitialMargin    -> "totalInitialMargin",
      _.totalAvailableBalance -> "totalAvailableBalance",
      _.totalWalletBalance    -> "totalWalletBalance",
      _.ts_bybit              -> "ts_bybit"
    )
  }

  private val WalletBalanceCoinSchema = quote {
    querySchema[WalletBalanceCoinInsert](
      "data.wallet_balance_coin",
      _.id_wallet_balance -> "id_wallet_balance",
      _.id_coin           -> "id_coin",
      _.equity            -> "equity",
      _.usdValue          -> "usdValue",
      _.cumRealisedPnl    -> "cumRealisedPnl"
    )
  }

  private val OrderItemInfoSchema = quote {
    querySchema[OrderItemInfoInsert](
      "data.bb_order",
      _.orderId               -> "orderId",
      _.orderLinkId           -> "orderLinkId",
      _.symbol                -> "symbol",
      _.price                 -> "price",
      _.qty                   -> "qty",
      _.side                  -> "side",
      _.isLeverage            -> "isLeverage",
      _.orderStatus           -> "orderStatus",
      _.cancelType            -> "cancelType",
      _.rejectReason          -> "rejectReason",
      _.avgPrice              -> "avgPrice",
      _.cumExecFee            -> "cumExecFee",
      _.slippageToleranceType -> "slippageToleranceType",
      _.marketUnit            -> "marketUnit",
      _.slippageTolerance     -> "slippageTolerance"
    )
  }

  private val ErrorLogSchema = quote {
    querySchema[ErrorLog](
      "data.common_log",
      _.id_loglevel -> "id_log_level",
      _.bb_module   -> "module",
      _.bb_action   -> "action",
      _.error_class -> "error_class",
      _.msg         -> "msg"
    )
  }

  private val adviceSchema = quote {
    querySchema[Advice](
      "data.advice",
      _.adviser_id -> "adviser_id",
      _.id_candle  -> "id_candle",
      _.id_symbol  -> "id_symbol",
      _.ts_db      -> "ts_db",
      _.start_ts   -> "start_ts",
      _.end_ts     -> "end_ts",
      _.c_interval -> "c_interval",
      _.o          -> "o",
      _.h          -> "h",
      _.l          -> "l",
      _.c          -> "c",
      _.v          -> "v",
      _.advice     -> "advice"
    )
  }

  private val adviceInsertSchema = quote {
    querySchema[AdviceInsert](
      "data.advice",
      _.id         -> "id",
      _.adviser_id -> "adviser_id",
      _.id_candle  -> "id_candle",
      _.id_symbol  -> "id_symbol",
      _.ts_db      -> "ts_db",
      _.start_ts   -> "start_ts",
      _.end_ts     -> "end_ts",
      _.c_interval -> "c_interval",
      _.o          -> "o",
      _.h          -> "h",
      _.l          -> "l",
      _.c          -> "c",
      _.v          -> "v",
      _.advice     -> "advice"
    )
  }

  private val InsertedCandle = quote {
    querySchema[InsertedCandle](
      "data.candle",
      _.id         -> "id",
      _.id_symbol  -> "id_symbol",
      _.ts_db      -> "ts_db",
      _.start_ts   -> "start_ts",
      _.end_ts     -> "end_ts",
      _.c_interval -> "c_interval",
      _.o          -> "o",
      _.h          -> "h",
      _.l          -> "l",
      _.c          -> "c",
      _.v          -> "v"
    )
  }

  private val futuresDataInsertSchema = quote {
    querySchema[FuturesDataRow](
      "data.futures_data",
      _.idSymbol              -> "id_symbol",
      _.lastPrice             -> "last_price",
      _.indexPrice            -> "index_price",
      _.markPrice             -> "mark_price",
      _.prevPrice24h          -> "prev_price_24h",
      _.price24hPcnt          -> "price_24h_pcnt",
      _.highPrice24h          -> "high_price_24h",
      _.lowPrice24h           -> "low_price_24h",
      _.prevPrice1h           -> "prev_price_1h",
      _.openInterest          -> "open_interest",
      _.openInterestValue     -> "open_interest_value",
      _.turnover24h           -> "turnover_24h",
      _.volume24h             -> "volume_24h",
      _.fundingRate           -> "funding_rate",
      _.nextFundingTime       -> "next_funding_time",
      _.ask1Size              -> "ask1_size",
      _.bid1Price             -> "bid1_price",
      _.ask1Price             -> "ask1_price",
      _.bid1Size              -> "bid1_size",
      _.fundingIntervalHour   -> "funding_interval_hour",
      _.fundingCap            -> "funding_cap"
    )
  }

  override def getSymbols: ZIO[DataSource, SQLException, Set[Symbol]] =
    run(symbolSchema.filter(_.is_enabled == lift(true))).map(_.toSet)

  override def getCoins: ZIO[DataSource, SQLException, Set[Coin]] =
    run(coinSchema).map(_.toSet)

  override def getLogLevels: ZIO[DataSource, SQLException, Set[LogLevel]] =
    run(LogLevelSchema).map(_.toSet)

  override def refSymbolsIntervals(symbols: Set[SymbolId]): ZIO[DataSource, SQLException, Set[RefSymbolsIntervals]] = {
    val q = quote {
      val idSet = liftQuery(symbols)
      refSymbolsIntervalsSchema.filter(r => idSet.contains(r.id_symbol))
    }
    run(q).map(_.toSet)
  }

  private def saveSnapshot(obInsert: OrderBookSnapshotInsert): ZIO[DataSource, SQLException, Long] =
    run(
      orderBookSnapshotSchema
        .insert(
          _.id_symbol -> lift(obInsert.id_symbol),
          _.ts_bybit  -> lift(obInsert.ts_bybit)
        )
        .returning(insertedRow => insertedRow.id)
    )

  private def saveCandle(
    klineTopic: KLineTopic,
    candleInsert: CandleInsert,
    ref: Ref[Map[KLineTopic, CurrentCandle]]
  ): ZIO[DataSource, SQLException, Long] = for {
    prev_id <- ref.get.map(_(klineTopic).id_candle)

    /**
     * when prev_id !=0 then normal flow of saving, when prev_id == 0 it can be 2 cases: 1) start of whole application
     * 2) restarted service when was problem with internet
     */
    _      <-
      if (prev_id != 0)
        run(
          CandleSchema
            .filter(_.id == lift(prev_id))
            .update(
              _.start_ts -> lift(candleInsert.start_ts),
              _.end_ts   -> lift(candleInsert.end_ts),
              _.o        -> lift(candleInsert.o),
              _.h        -> lift(candleInsert.h),
              _.l        -> lift(candleInsert.l),
              _.c        -> lift(candleInsert.c),
              _.v        -> lift(candleInsert.v)
            )
        )
      else
        run(
          CandleSchema
            .filter(c =>
              c.id_symbol == lift(candleInsert.id_symbol) &&
                c.c_interval == lift(candleInsert.c_interval) &&
                c.start_ts.isEmpty
            )
            .delete
        )
    new_id <- run(
      CandleSchema
        .insert(
          _.id_symbol  -> lift(candleInsert.id_symbol),
          _.c_interval -> lift(candleInsert.c_interval)
        )
        .returning(_.id)
    )
    _      <- ZIO.logDebug(s"saveCandle prev_id = $prev_id new_id = $new_id")
    _      <- ref.update { m =>
      m.get(klineTopic) match {
        case Some(c) => m.updated(klineTopic, c.copy(id_candle = new_id))
        case None    => m
      }
    }
  } yield new_id

  private def saveKLine(klineInsert: KLineInsert) = for {
    _ <- run(
      KLineSchema.insert(
        _.id_candle -> lift(klineInsert.id_candle),
        _.start_ts  -> lift(klineInsert.start_ts),
        _.end_ts    -> lift(klineInsert.end_ts),
        _.o         -> lift(klineInsert.o),
        _.h         -> lift(klineInsert.h),
        _.l         -> lift(klineInsert.l),
        _.c         -> lift(klineInsert.c),
        _.v         -> lift(klineInsert.v)
      )
    )
  } yield ()

  override def saveOrderBook(symbol: Symbol, orderBook: OrderBookResult): ZIO[DataSource, SQLException, Unit] = for {
    snapshotInsertedId <- saveSnapshot(OrderBookResConverter.obResToSnapshotInsert(symbol, orderBook))
    batchRows           = OrderBookResultConverter.obResToOrderBookResultInsert(snapshotInsertedId, orderBook)
    _                  <- run(
      liftQuery(batchRows).foreach { row =>
        orderBookResultInsertSchema.insert(
          _.id_snapshot -> row.id_snapshot,
          _.side        -> row.side,
          _.price       -> row.price,
          _.amount      -> row.amount
        )
      }
    )
  } yield ()

  override def saveOpenInterest(symbol: Symbol, openInterest: OpenInterestResult): ZIO[DataSource, SQLException, Unit] =
    for {
      _ <- run(
        liftQuery(OpenInterestResConverter.oiResToOpenInterestInsert(symbol, openInterest)).foreach { row =>
          openInterestInsertSchema.insert(
            _.id_symbol -> row.id_symbol,
            _.ts_bybit  -> row.ts_bybit,
            _.oi        -> row.oi
          )
        }
      )
    } yield ()

  override def saveFuturesData(futuresData: FuturesDataResult): ZIO[DataSource, SQLException, Unit] = for {
    //Add new symbols if not exists in table data.symbol_futures
    _ <- ctx.run(quote {
      liftQuery(futuresData.list.map(_.symbol).distinct).foreach { s =>
        symbolFutures
          .insert(
            _.code -> s
          )
          .onConflictIgnore(_.code)
      }
    })

    symbolIdMap <- ctx.run(quote {
      symbolFutures
        .filter(s => liftQuery(futuresData.list.map(_.symbol).distinct).contains(s.code))
        .map(s => s.code -> s.id)
    }).map(_.toMap)

    //only Observed symbols, store data in data.futures_data_observed
    symbolIdObservedMap <- ctx.run(quote {
      symbolFutures
        .filter(s => liftQuery(futuresData.list.map(_.symbol).distinct).contains(s.code)).filter(_.isObserved)
        .map(s => s.code -> s.id)
    }).map(_.toMap)

    rowsSymbol = futuresData.list.flatMap { fd =>
      symbolIdMap.get(fd.symbol).map { idSymbol =>
        FuturesDataRow.fromFuturesData(fd, idSymbol)
      }
    }

    rowsSymbolObserved = futuresData.list.filter(fd => symbolIdObservedMap.contains(fd.symbol)).flatMap { fd =>
      symbolIdObservedMap.get(fd.symbol).map { idSymbol =>
        FuturesDataRow.fromFuturesData(fd, idSymbol)
      }
    }

    _ <- ctx.run(quote {
      liftQuery(rowsSymbolObserved).foreach { row =>
        FuturesDataObservedRowSchema.insert(
          _.idSymbol -> row.idSymbol,
          _.lastPrice -> row.lastPrice,
          _.indexPrice -> row.indexPrice,
          _.markPrice -> row.markPrice,
          _.prevPrice24h -> row.prevPrice24h,
          _.price24hPcnt -> row.price24hPcnt,
          _.highPrice24h -> row.highPrice24h,
          _.lowPrice24h -> row.lowPrice24h,
          _.prevPrice1h -> row.prevPrice1h,
          _.openInterest -> row.openInterest,
          _.openInterestValue -> row.openInterestValue,
          _.turnover24h -> row.turnover24h,
          _.volume24h -> row.volume24h,
          _.fundingRate -> row.fundingRate,
          _.nextFundingTime -> row.nextFundingTime,
          _.ask1Size -> row.ask1Size,
          _.bid1Price -> row.bid1Price,
          _.ask1Price -> row.ask1Price,
          _.bid1Size -> row.bid1Size,
          _.fundingIntervalHour -> row.fundingIntervalHour,
          _.fundingCap -> row.fundingCap
        )
      }
    })

    _ <- ctx.run(quote {
      liftQuery(rowsSymbol).foreach { row =>
        FuturesDataRowSchema.insert(
          _.idSymbol -> row.idSymbol,
          _.lastPrice -> row.lastPrice,
          _.indexPrice -> row.indexPrice,
          _.markPrice -> row.markPrice,
          _.prevPrice24h -> row.prevPrice24h,
          _.price24hPcnt -> row.price24hPcnt,
          _.highPrice24h -> row.highPrice24h,
          _.lowPrice24h -> row.lowPrice24h,
          _.prevPrice1h -> row.prevPrice1h,
          _.openInterest -> row.openInterest,
          _.openInterestValue -> row.openInterestValue,
          _.turnover24h -> row.turnover24h,
          _.volume24h -> row.volume24h,
          _.fundingRate -> row.fundingRate,
          _.nextFundingTime -> row.nextFundingTime,
          _.ask1Size -> row.ask1Size,
          _.bid1Price -> row.bid1Price,
          _.ask1Price -> row.ask1Price,
          _.bid1Size -> row.bid1Size,
          _.fundingIntervalHour -> row.fundingIntervalHour,
          _.fundingCap -> row.fundingCap
        )
      }
    })

  } yield ()

  /**
   * Save data in 2 tables: data.candle or data.kline. If data is confirmed, save into data.candle with updating
   * Ref[Map]. If data is not confirm, save into data.kline with foreign key to data.candle.
   */
  override def saveBar(
    kline: KLine,
    ref: Ref[Map[KLineTopic, CurrentCandle]],
    symbol: Symbol
  ): ZIO[DataSource, SQLException, Unit] = for {
    parsedKLineTopic <- ZIO.succeed(KLineTopic.parse(kline.topic, symbol))
    _                <- ref
      .get
      .flatMap(_.get(parsedKLineTopic) match {
        // save into data.candle with updating Ref[Map]
        case Some(_) if kline.data.confirm                                        =>
          saveCandle(parsedKLineTopic, kline.data.toCandleInsert(symbol.id), ref)
        // save into data.kline with foreign key to data.candle
        case Some(currCandle) if currCandle.id_candle != 0 && !kline.data.confirm =>
          saveKLine(kline.data.toKLineInsert(currCandle.id_candle))
        case _                                                                    => ZIO.unit
      })
  } yield ()

  /**
   * Save data in data.wallet_balance with returning id. Save data in data.wallet_balance_coin with foreign key to
   * data.wallet_balance.
   */
  override def saveWalletBalance(
    coins: Set[Coin],
    walletBalance: ApiRespWalletBalance
  ): ZIO[DataSource, SQLException, Unit] = for {
    walletBalanceId <- run(
      WalletBalanceSchema
        .insertValue(
          lift(
            walletBalance.result.balance.toWalletBalanceInsert(walletBalance.time)
          )
        )
        .returningGenerated(_.id)
    )
    _               <- ZIO.logDebug(s"wallet_balance.id = $walletBalanceId")
    coinsForInsert   = walletBalance.result.balance.coin.map { coinBalance =>
      coinBalance.toWalletBalanceCoinInsert(
        id_wallet_balance = walletBalanceId,
        id_coin = coins.find(_.code == coinBalance.coin).map(_.id)
      )
    }
    // batch insert
    _               <- run(liftQuery(coinsForInsert).foreach(c => WalletBalanceCoinSchema.insertValue(c)))
  } yield ()

  override def getCommonWalletBalance(maxWalletBalanceId: Long): ZIO[DataSource, SQLException, CommonWalletBalance] = {
    val q = quote {
      sql"""
      select wb.totalequity,
             (round(EXTRACT(EPOCH FROM (current_timestamp-to_timestamp(wb.ts_bybit::double precision/1000.0)))) < 10) as is_actual,
              round(EXTRACT(EPOCH FROM (current_timestamp-to_timestamp(wb.ts_bybit::double precision/1000.0))))       as diff_seconds,
             to_char(current_timestamp, 'DD.MM.YYYY HH24:MI:SS')                                  as ts_current,
             to_char(to_timestamp(wb.ts_bybit::double precision/1000.0), 'DD.MM.YYYY HH24:MI:SS') as ts_bybit
      from data.wallet_balance wb
      where wb.id = ${liftScalar(maxWalletBalanceId)}
       """.as[Query[CommonWalletBalance]]
    }
    run(q).flatMap {
      case balance :: _ => ZIO.succeed(balance)
      case Nil          => ZIO.fail(new SQLException("No wallet_balance rows found in data.wallet_balance"))
    }
  }

  override def getMaxWalletBalanceId: ZIO[DataSource, SQLException, Long] =
    run(WalletBalanceSchema.map(_.id).max)
      .map(_.getOrElse(0L))

  override def getSymbolsBalance(maxWalletBalanceId: Long): ZIO[DataSource, SQLException, List[SymbolsBalance]] = {
    val q = quote {
      (for {
        wbc <- WalletBalanceCoinSchema if wbc.id_wallet_balance == lift(maxWalletBalanceId)
        c   <- coinSchema if c.id == wbc.id_coin
        s   <- symbolSourceSchema.leftJoin(s => s.id_coin == c.id)
      } yield SymbolsBalance(
        s.map(_.id),
        s.map(_.code),
        c.code,
        s.map(_.is_enabled),
        wbc.equity,
        wbc.usdValue
      )).sortBy(_.usdvalue)(Ord.desc)
    }
    run(q)
  }

  /*
  temporary closed

  override def getTradeAdvice(symbol: Symbol): ZIO[DataSource, SQLException, Option[TradeAdviceSelect]] = for {
    advice <- run(tradeAdviceSchema.filter(_.id_symbol == lift(symbol.id))).map(_.headOption)
    _      <- advice match {
      case Some(adv) =>
        ZIO.logInfo(s"Branch SOME adv.trade_advice_id = ${adv.trade_advice_id}") *>
          run(
            tradeAdviceUpdateSchema
              .filter(_.id == lift(adv.trade_advice_id))
              .update(
                _.is_taken -> lift(true),
                _.taken_ts -> sql"localtimestamp".as[Instant]
              )
          ).unit
      case None      =>
        ZIO.unit
    }
  } yield advice

  override def saveTradeAdviceOrder(order: TradeAdviceOrder): ZIO[DataSource, SQLException, Unit] = for {
    _ <- run(
      TradeAdviceOrderSchema.insert(
        _.orderId         -> lift(order.orderId),
        _.id_trade_advice -> lift(order.id_trade_advice),
        _.ts_bybit        -> lift(order.ts_bybit)
      )
    )
  } yield ()
   */

  override def saveOrderHistory(order: OrderItemInfoInsert): ZIO[DataSource, SQLException, Unit] = for {
    _ <- run(
      OrderItemInfoSchema.insert(
        _.orderId               -> lift(order.orderId),
        _.orderLinkId           -> lift(order.orderLinkId),
        _.symbol                -> lift(order.symbol),
        _.price                 -> lift(order.price),
        _.qty                   -> lift(order.qty),
        _.side                  -> lift(order.side),
        _.isLeverage            -> lift(order.isLeverage),
        _.orderStatus           -> lift(order.orderStatus),
        _.cancelType            -> lift(order.cancelType),
        _.rejectReason          -> lift(order.rejectReason),
        _.avgPrice              -> lift(order.avgPrice),
        _.cumExecFee            -> lift(order.cumExecFee),
        _.slippageToleranceType -> lift(order.slippageToleranceType),
        _.marketUnit            -> lift(order.marketUnit),
        _.slippageTolerance     -> lift(order.slippageTolerance)
      )
    )
  } yield ()

  /*
  override def isAvailiable: ZIO[DataSource, SQLException, Boolean] = for {
    ds      <- ZIO.service[DataSource]
    isAvail <- ZIO
      .attemptBlockingInterrupt(ds.getConnection())
      .tapBoth(
        err => ZIO.logError(s"Error getting connection: ${err.getMessage}"),
        conn => ZIO.attempt(conn.close())
      )
      .foldZIO(
        _ => ZIO.succeed(false),
        _ => ZIO.succeed(true)
      )
      .timeout(zio.Duration.fromSeconds(5))
      .map {
        case Some(_) => true
        case None    => false
      }
  } yield isAvail
   */
  override def isAvailiable: ZIO[DataSource, SQLException, Boolean] = {
    val check = for {
      ds <- ZIO.service[DataSource]
      _  <- ZIO
        .attemptBlockingInterrupt(ds.getConnection())
        .tapBoth(
          err => ZIO.logError(s"Error getting connection: ${err.getMessage}"),
          conn => ZIO.attempt(conn.close())
        )
    } yield true

    check
      .timeout(zio.Duration.fromSeconds(5))
      .map(_.getOrElse(false)) // false при таймауте
      .catchAll {
        case _: TimeoutException => ZIO.succeed(false)
        case e                   => ZIO.fail(new SQLException("DB check failed", e))
      }
  }

  override def saveLogInDb(err: ErrorLog): ZIO[DataSource, SQLException, Unit] = for {
    _ <- ZIO.logInfo(s"POSTGRES DB AVAILABLE SAVE LOG ${err.error_class}")
    _ <- run(
      ErrorLogSchema.insert(
        _.id_loglevel -> lift(err.id_loglevel),
        _.bb_module   -> lift(err.bb_module),
        _.bb_action   -> lift(err.bb_action),
        _.error_class -> lift(err.error_class),
        _.msg         -> lift(err.msg)
      )
    )
  } yield ()

  override def executeReglamentCleanup(code: String): ZIO[DataSource, SQLException, Unit] = {

    val reglamentSchema: Quoted[EntityQuery[ReglamentRow]] = quote {
      querySchema[ReglamentRow]("data.reglament")
        .filter(_.code == lift(code))
    }

    val reglamentLogSchema = quote {
      querySchema[ReglamentLog](
        "data.reglament_log",
        _.id           -> "id",
        _.id_reglament -> "id_reglament",
        _.end_ts       -> "end_ts",
        _.deleted_rows -> "deleted_rows"
      )
    }

    def deleteWalletBalance(intVal: Int): ZIO[DataSource, SQLException, Long] = {
      val cutoff  = quote {
        sql"""(select max(ts_bybit) - (${lift(intVal)}::bigint * 24 * 60 * 60 * 1000)
                   from data.wallet_balance)""".as[Long]
      }
      val deleteQ = quote {
        querySchema[WalletBalanceInsert]("data.wallet_balance")
          .filter(wb => wb.ts_bybit < cutoff)
          .delete
      }
      ZIO.logInfo(s"DB - deleteWalletBalance for intVal = $intVal") *>
        run(deleteQ)
    }

    def deleteOrderBookSnapshot(intVal: Int): ZIO[DataSource, SQLException, Long] = {
      val cutoff  = quote {
        sql"""(select max(ts_bybit) - (${lift(intVal)}::bigint * 24 * 60 * 60 * 1000)
                   from data.order_book_snapshot)""".as[Long]
      }
      val deleteQ = quote {
        querySchema[OrderBookSnapshotInsert]("data.order_book_snapshot")
          .filter(wb => wb.ts_bybit < cutoff)
          .delete
      }
      ZIO.logInfo(s"DB - deleteOrderBookSnapshot for intVal = $intVal") *>
        run(deleteQ)
    }

    def deleteCandle(intVal: Int): ZIO[DataSource, SQLException, Long] = {
      val cutoff  = quote {
        sql"""(select max(start_ts) - (${lift(intVal)}::bigint * 24 * 60 * 60 * 1000)
                   from data.candle
                  where start_ts is not null)""".as[Long]
      }
      val deleteQ = quote {
        querySchema[CandleInsert]("data.candle")
          .filter(wb => wb.start_ts.isDefined && wb.start_ts.getOrElse(0L) < cutoff)
          .delete
      }
      ZIO.logInfo(s"DB - deleteCandle for intVal = $intVal") *>
        run(deleteQ)
    }

    def deleteOI(intVal: Int): ZIO[DataSource, SQLException, Long] = {
      val cutoff  = quote {
        sql"""(select max(ts_bybit) - (${lift(intVal)}::bigint * 24 * 60 * 60 * 1000)
                   from data.open_interest)""".as[Long]
      }
      val deleteQ = quote {
        querySchema[OpenInterestInsert]("data.open_interest")
          .filter(wb => wb.ts_bybit < cutoff)
          .delete
      }
      ZIO.logInfo(s"DB - OpenInterestInsert for intVal = $intVal") *>
        run(deleteQ)
    }

    for {
      // reglamentParams contains just one row by input code for executeReglamentCleanup
      _            <- ZIO.logInfo(s"executeReglamentCleanup code = $code")
      regParameter <- run(reglamentSchema)
        // .map(_.head)
        .flatMap {
          case Nil      => ZIO.fail(new SQLException(s"Multiple rows in data.reglament by filtering by code = $code"))
          case reg :: _ => ZIO.succeed(reg)
        }

      logId <- run(
        reglamentLogSchema
          .insert(_.id_reglament -> lift(regParameter.id))
          .returning(_.id)
      )

      deletedRows <- code match {
        case "keep_wb_days"     => deleteWalletBalance(regParameter.int_val)
        case "keep_obs_days"    => deleteOrderBookSnapshot(regParameter.int_val)
        case "keep_candle_days" => deleteCandle(regParameter.int_val)
        case "keep_oi_days"     => deleteOI(regParameter.int_val)
        case _                  => ZIO.succeed(0L)
      }
      _           <- ZIO.logInfo(s"deletedRows = $deletedRows")
      _           <- run(quote {
        reglamentLogSchema
          .filter(_.id == lift(logId))
          .update(
            _.end_ts       -> sql"localtimestamp".as[Option[java.sql.Timestamp]],
            _.deleted_rows -> lift(deletedRows)
          )
      })
    } yield ()
  }

  def getSymbolAdviceProcs(mins: Int): ZIO[DataSource, SQLException, List[SymbolAdviceProc]] = {
    val q = quote {
      for {
        am  <- adviceMetaSchema
        rmi <- refAdviceMetaIntervalSchema.join(_.id_advice_meta == am.id)
        i   <- intervalSchema.join(_.id == rmi.id_interval)
        vs  <- symbolSchema
        if i.mins.isDefined && i.mins.contains(lift(mins)) &&
          vs.is_enabled && vs.is_tradable
      } yield SymbolAdviceProc(
        adviserId = am.id,
        idSymbol = vs.id,
        procedureName = am.proc,
        mins = i.mins.getOrElse(0)
      )
    }
    run(q)
  }

  def getAdviceIntervals: ZIO[DataSource, SQLException, List[IntervalIntMins]] = {
    val q = quote {
      (for {
        am  <- adviceMetaSchema
        rmi <- refAdviceMetaIntervalSchema.join(_.id_advice_meta == am.id)
        i   <- intervalSchema.join(_.id == rmi.id_interval)
        vs  <- symbolSchema
        if i.mins.isDefined && vs.is_enabled && vs.is_tradable
      } yield i.mins.getOrElse(0)).distinct
    }
    run(q)
  }

  private def getAdvice(sap: SymbolsAdviceProc): ZIO[DataSource, SQLException, List[Advice]] = {
    val q = quote {
      val advisorIdLifted = lift(sap.adviserId)
      val idSymbolsLifted = lift(sap.idSymbols.toList)
      val cIntervalLifted = lift(sap.mins.toString)
      val funcName        = lift(sap.func)

      val subQuery: Query[(Int, Option[Timestamp])] =
        InsertedCandle
          .filter(c =>
            idSymbolsLifted.contains(c.id_symbol) &&
              c.start_ts.isDefined &&
              c.c_interval == cIntervalLifted
          )
          .groupBy(_.id_symbol)
          .map { case (symbol, g) =>
            (symbol, g.map(_.ts_db).max)
          }
      InsertedCandle
        .filter(c =>
          c.start_ts.isDefined &&
            c.c_interval == cIntervalLifted
        )
        .filter(c =>
          subQuery.contains((c.id_symbol, c.ts_db)) &&
            // ---------------------------------------------------------------
            // not exists filter
            !adviceInsertSchema
              .filter(a =>
                a.adviser_id == advisorIdLifted &&
                  a.id_symbol == c.id_symbol &&
                  a.c_interval == c.c_interval &&
                  c.start_ts.isDefined &&
                  a.start_ts >= (c.start_ts.map(_ - 17100000L).getOrElse(0L)) &&
                  a.start_ts <= (c.start_ts.map(_ - 1L).getOrElse(0L))
              )
              .nonEmpty &&
            // ---------------------------------------------------------------
            sql"data.call_advice_function($funcName, ${c.id_symbol}, ${c.start_ts})".as[Option[String]].isDefined
        )
        .map { c =>
          Advice(
            adviser_id = advisorIdLifted,
            id_candle = c.id,
            id_symbol = c.id_symbol,
            ts_db = c.ts_db,
            start_ts = c.start_ts.getOrElse(0L),
            end_ts = c.end_ts,
            c_interval = c.c_interval,
            o = c.o,
            h = c.h,
            l = c.l,
            c = c.c,
            v = c.v,
            advice = sql"data.call_advice_function($funcName, ${c.id_symbol}, ${c.start_ts})".as[Option[String]]
          )
        }
    }
    run(q)
  }

  private def saveAdvice(advice: List[Advice]): ZIO[DataSource, SQLException, List[AdviceId]] =
    ZIO.logInfo(s"saveAdvice size = ${advice.size}").when(advice.nonEmpty) *>
      ZIO.foreach(advice) { a =>
        val q = quote {
          adviceInsertSchema
            .insert(
              _.adviser_id -> lift(a.adviser_id),
              _.id_candle  -> lift(a.id_candle),
              _.id_symbol  -> lift(a.id_symbol),
              _.ts_db      -> lift(a.ts_db),
              _.start_ts   -> lift(a.start_ts),
              _.end_ts     -> lift(a.end_ts),
              _.c_interval -> lift(a.c_interval),
              _.o          -> lift(a.o),
              _.h          -> lift(a.h),
              _.l          -> lift(a.l),
              _.c          -> lift(a.c),
              _.v          -> lift(a.v),
              _.advice     -> lift(a.advice)
            )
            .returning(_.id)
        }
        run(q)
      }

  override def getAndSaveAdvice(sap: SymbolsAdviceProc): ZIO[DataSource, SQLException, List[AdviceId]] = for {
    advice <- getAdvice(sap)
      .tapError(e => ZIO.logError(s"SQL fail: ${e.getMessage}"))
      .tapDefect(cause => ZIO.logError(s"Defect in getAdvice: ${cause.prettyPrint}"))
      .catchAllDefect(defect => ZIO.fail(new SQLException("Defect in getAdvice", defect)))
    ids    <- saveAdvice(advice)
  } yield ids

  /**
   * Reads unsent tips from the database and marks them as data.advice.is_sent_to_user = true
   */
  override def getAllAdvice(): ZIO[DataSource, SQLException, List[AdviceToUser]] = {
    val q = quote {
      for {
        a  <- adviceInsertSchema
        am <- adviceMetaSchema.join(_.id == a.adviser_id)
        s  <- symbolShort.join(_.id == a.id_symbol)
        if !a.is_sent_to_user
      } yield AdviceToUser(
        adviser_id = a.adviser_id,
        advice_description = am.advice_description,
        proc = am.proc,
        advice_id = a.id,
        id_candle = a.id_candle,
        start_ts = a.start_ts,
        id_symbol = a.id_symbol,
        symbol_code = s.code,
        ts_db = a.ts_db,
        c_interval = a.c_interval,
        last_price = a.c,
        advice = a.advice.getOrElse("-")
      )
    }

    for {
      adviceList <- run(q)
      ids         = adviceList.map(_.advice_id)
      _          <- ZIO.when(ids.nonEmpty) {
        run(quote {
          adviceInsertSchema
            .filter(a => liftQuery(ids).contains(a.id))
            .update(_.is_sent_to_user -> lift(true))
        })
      }
    } yield adviceList
  }

  override def getViewDeep(interval: String, deep_bars: Int): ZIO[DataSource, SQLException, List[ViewDeepLine]] = {
    val query = quote {
      sql"""
      SELECT * FROM data.view_deep(
        p_interval => ${lift(interval)},
        p_deep_bars => ${lift(deep_bars)}
      )
    """.as[Query[ViewDeepLine]]
    }
    ctx.run(query)
  }


}
