package bybit_model

import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

import scala.util.Try

/**
 * Bybit `/v5/position/list` response for category=linear. Numbers come as strings, and unused fields may be empty
 * strings, so every numeric field is decoded into `Option` via [[FuturesPositionParsers.optDecimal]].
 */
case class ApiRespPositions(
  retCode: Int,
  retMsg: String,
  result: PositionsResult,
  retExtInfo: Option[Map[String, String]],
  time: Option[Long]
)

case class PositionsResult(
  category: String,
  list: List[PositionInfo],
  nextPageCursor: Option[String]
)

/**
 * One open position row. `size` is 0 for a position that was just closed, so callers must filter it out before storing
 * a snapshot.
 */
case class PositionInfo(
  symbol: String,
  side: String,
  size: Option[BigDecimal],
  avgPrice: Option[BigDecimal],
  markPrice: Option[BigDecimal],
  liqPrice: Option[BigDecimal],
  positionValue: Option[BigDecimal],
  leverage: Option[BigDecimal],
  positionIdx: Option[Int],
  unrealisedPnl: Option[BigDecimal],
  curRealisedPnl: Option[BigDecimal],
  cumRealisedPnl: Option[BigDecimal],
  takeProfit: Option[BigDecimal],
  stopLoss: Option[BigDecimal],
  updatedTime: Option[Long]
)

/** Bybit `/v5/position/closed-pnl` response for category=linear. */
case class ApiRespClosedPnl(
  retCode: Int,
  retMsg: String,
  result: ClosedPnlResult,
  retExtInfo: Option[Map[String, String]],
  time: Option[Long]
)

case class ClosedPnlResult(
  category: String,
  list: List[ClosedPnlItem],
  nextPageCursor: Option[String]
)

/** One closed-pnl record, stored as returned by Bybit. */
case class ClosedPnlItem(
  symbol: String,
  side: String,
  orderId: Option[String],
  orderPrice: Option[BigDecimal],
  orderType: Option[String],
  execType: Option[String],
  closedSize: Option[BigDecimal],
  cumEntryValue: Option[BigDecimal],
  avgEntryPrice: Option[BigDecimal],
  cumExitValue: Option[BigDecimal],
  avgExitPrice: Option[BigDecimal],
  closedPnl: Option[BigDecimal],
  leverage: Option[BigDecimal],
  updatedTime: Option[Long],
  createdTime: Option[Long]
)

/** Row of data.futures_positions_config: the single online configuration row of the positions collector. */
case class FuturesPositionsConfig(
  id: Int,
  openPollSecs: Int,
  closedPollSecs: Int,
  closedLookbackMin: Int,
  isEnabled: Boolean
)

/** Row to insert into data.futures_position (an append-only snapshot of one open position). */
case class FuturesPositionInsert(
  idSymbolFutures: Int,
  side: String,
  size: Option[BigDecimal],
  avgPrice: Option[BigDecimal],
  markPrice: Option[BigDecimal],
  liqPrice: Option[BigDecimal],
  positionValue: Option[BigDecimal],
  leverage: Option[BigDecimal],
  positionIdx: Option[Int],
  unrealisedPnl: Option[BigDecimal],
  curRealisedPnl: Option[BigDecimal],
  cumRealisedPnl: Option[BigDecimal],
  takeProfit: Option[BigDecimal],
  stopLoss: Option[BigDecimal]
)

/** Row to insert into data.futures_position_closed. */
case class FuturesPositionClosedInsert(
  idSymbolFutures: Int,
  side: String,
  orderId: Option[String],
  orderPrice: Option[BigDecimal],
  orderType: Option[String],
  execType: Option[String],
  closedSize: Option[BigDecimal],
  cumEntryValue: Option[BigDecimal],
  avgEntryPrice: Option[BigDecimal],
  cumExitValue: Option[BigDecimal],
  avgExitPrice: Option[BigDecimal],
  closedPnl: Option[BigDecimal],
  leverage: Option[BigDecimal],
  createdTime: Option[Long],
  updatedTime: Option[Long]
)

/** Helpers to parse Bybit payloads, where numbers are strings and an empty string means "no value". */
object FuturesPositionParsers {
  def optDecimal(s: Option[String]): Option[BigDecimal] =
    s.map(_.trim).filter(_.nonEmpty).flatMap(v => Try(BigDecimal(v)).toOption)

  def optLong(s: Option[String]): Option[Long] =
    s.map(_.trim).filter(_.nonEmpty).flatMap(v => Try(v.toLong).toOption)

  def optInt(s: Option[String]): Option[Int] =
    s.map(_.trim).filter(_.nonEmpty).flatMap(v => Try(v.toInt).toOption)

  def optString(s: Option[String]): Option[String] =
    s.map(_.trim).filter(_.nonEmpty)
}

object ApiRespPositions {
  implicit val decoder: JsonDecoder[ApiRespPositions] = DeriveJsonDecoder.gen[ApiRespPositions]
  implicit val encoder: JsonEncoder[ApiRespPositions] = DeriveJsonEncoder.gen[ApiRespPositions]
}

object PositionsResult {
  private case class RawPositionInfo(
    symbol: String,
    side: String,
    size: Option[String],
    avgPrice: Option[String],
    markPrice: Option[String],
    liqPrice: Option[String],
    positionValue: Option[String],
    leverage: Option[String],
    positionIdx: Option[Int],
    unrealisedPnl: Option[String],
    curRealisedPnl: Option[String],
    cumRealisedPnl: Option[String],
    takeProfit: Option[String],
    stopLoss: Option[String],
    updatedTime: Option[String]
  )

  private case class RawPositionsResult(
    category: String,
    list: List[RawPositionInfo],
    nextPageCursor: Option[String]
  )

  private implicit val rawItemDecoder: JsonDecoder[RawPositionInfo] = DeriveJsonDecoder.gen[RawPositionInfo]

  private implicit val rawDecoder: JsonDecoder[RawPositionsResult] = DeriveJsonDecoder.gen[RawPositionsResult]

  implicit val decoder: JsonDecoder[PositionsResult]  = rawDecoder.map { raw =>
    import FuturesPositionParsers._
    PositionsResult(
      category = raw.category,
      list = raw.list.map { r =>
        PositionInfo(
          symbol = r.symbol,
          side = r.side,
          size = optDecimal(r.size),
          avgPrice = optDecimal(r.avgPrice),
          markPrice = optDecimal(r.markPrice),
          liqPrice = optDecimal(r.liqPrice),
          positionValue = optDecimal(r.positionValue),
          leverage = optDecimal(r.leverage),
          positionIdx = r.positionIdx,
          unrealisedPnl = optDecimal(r.unrealisedPnl),
          curRealisedPnl = optDecimal(r.curRealisedPnl),
          cumRealisedPnl = optDecimal(r.cumRealisedPnl),
          takeProfit = optDecimal(r.takeProfit),
          stopLoss = optDecimal(r.stopLoss),
          updatedTime = optLong(r.updatedTime)
        )
      },
      nextPageCursor = optString(raw.nextPageCursor)
    )
  }
  implicit val itemEncoder: JsonEncoder[PositionInfo] = DeriveJsonEncoder.gen[PositionInfo]
  implicit val encoder: JsonEncoder[PositionsResult]  = DeriveJsonEncoder.gen[PositionsResult]
}

object ApiRespClosedPnl {
  implicit val decoder: JsonDecoder[ApiRespClosedPnl] = DeriveJsonDecoder.gen[ApiRespClosedPnl]
  implicit val encoder: JsonEncoder[ApiRespClosedPnl] = DeriveJsonEncoder.gen[ApiRespClosedPnl]
}

object ClosedPnlResult {
  private case class RawClosedPnlItem(
    symbol: String,
    side: String,
    orderId: Option[String],
    orderPrice: Option[String],
    orderType: Option[String],
    execType: Option[String],
    closedSize: Option[String],
    cumEntryValue: Option[String],
    avgEntryPrice: Option[String],
    cumExitValue: Option[String],
    avgExitPrice: Option[String],
    closedPnl: Option[String],
    leverage: Option[String],
    updatedTime: Option[String],
    createdTime: Option[String]
  )

  private case class RawClosedPnlResult(
    category: String,
    list: List[RawClosedPnlItem],
    nextPageCursor: Option[String]
  )

  private implicit val rawItemDecoder: JsonDecoder[RawClosedPnlItem] = DeriveJsonDecoder.gen[RawClosedPnlItem]

  private implicit val rawDecoder: JsonDecoder[RawClosedPnlResult] = DeriveJsonDecoder.gen[RawClosedPnlResult]

  implicit val decoder: JsonDecoder[ClosedPnlResult]   = rawDecoder.map { raw =>
    import FuturesPositionParsers._
    ClosedPnlResult(
      category = raw.category,
      list = raw.list.map { r =>
        ClosedPnlItem(
          symbol = r.symbol,
          side = r.side,
          orderId = optString(r.orderId),
          orderPrice = optDecimal(r.orderPrice),
          orderType = optString(r.orderType),
          execType = optString(r.execType),
          closedSize = optDecimal(r.closedSize),
          cumEntryValue = optDecimal(r.cumEntryValue),
          avgEntryPrice = optDecimal(r.avgEntryPrice),
          cumExitValue = optDecimal(r.cumExitValue),
          avgExitPrice = optDecimal(r.avgExitPrice),
          closedPnl = optDecimal(r.closedPnl),
          leverage = optDecimal(r.leverage),
          updatedTime = optLong(r.updatedTime),
          createdTime = optLong(r.createdTime)
        )
      },
      nextPageCursor = optString(raw.nextPageCursor)
    )
  }
  implicit val itemEncoder: JsonEncoder[ClosedPnlItem] = DeriveJsonEncoder.gen[ClosedPnlItem]
  implicit val encoder: JsonEncoder[ClosedPnlResult]   = DeriveJsonEncoder.gen[ClosedPnlResult]
}
