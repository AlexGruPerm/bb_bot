package bybit_model

import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

import java.time.Instant

trait TradeAdvice

final case class MarketTradeAdvice(
  category: String,
  symbol: String,
  isLeverage: Int,
  side: String,
  orderType: String,
  qty: String,
  marketUnit: String,
  slippageToleranceType: String,
  slippageTolerance: String
) extends TradeAdvice

final case class LimitTradeAdvice(
  category: String,
  symbol: String,
  isLeverage: Int,
  side: String,
  orderType: String
) extends TradeAdvice

case class TradeAdviceSelect(
  trade_advice_id: Long,
  id_symbol: Int,
  category: String,
  symbol: String,
  isLeverage: Int,
  side: String,
  orderType: String,
  qty: String,
  marketUnit: String,
  slippageToleranceType: String,
  slippageTolerance: String
) {
  def toTradeAdvice: TradeAdvice =
    orderType match {
      case "Market" =>
        MarketTradeAdvice(
          category = category,
          symbol = symbol,
          isLeverage = isLeverage,
          side = side,
          orderType = orderType,
          qty = qty,
          marketUnit = marketUnit,
          slippageToleranceType = slippageToleranceType,
          slippageTolerance = slippageTolerance
        )
      case "Limit"  =>
        LimitTradeAdvice(
          category = category,
          symbol = symbol,
          isLeverage = isLeverage,
          side = side,
          orderType = orderType
        )
    }
}

case class TradeAdviceUpdate(
  id: Long,
  is_taken: Boolean,
  taken_ts: Instant
)

case class TradeAdviceOrder(
  orderId: Long,
  id_trade_advice: Long,
  ts_bybit: Long
) {
  def debug: String =
    s"""
       | id_trade_advice = $id_trade_advice =>
       |   opened order :
       |     orderId = $orderId
       |     time    = $ts_bybit
       |""".stripMargin
}

object MarketTradeAdvice {
  implicit val encoder: JsonEncoder[MarketTradeAdvice] = DeriveJsonEncoder.gen[MarketTradeAdvice]
  implicit val decoder: JsonDecoder[MarketTradeAdvice] = DeriveJsonDecoder.gen[MarketTradeAdvice]
}

object LimitTradeAdvice {
  implicit val encoder: JsonEncoder[LimitTradeAdvice] = DeriveJsonEncoder.gen[LimitTradeAdvice]
  implicit val decoder: JsonDecoder[LimitTradeAdvice] = DeriveJsonDecoder.gen[LimitTradeAdvice]
}

/*
final case class MarketTradeAdvice(
                        category: String = "spot",
                        symbol:   String = "MNTUSDT",
                        isLeverage: Int = 1,
                        side: String = "Buy",
                        orderType: String = "Market", //	Market, Limit
                        qty: String = "7.99",
                        marketUnit: String = "baseCoin", // baseCoin, quoteCoin
                        slippageToleranceType: String = "Percent",
                        slippageTolerance: String = "0.1" // 10%
                      ) extends TradeAdvice
 */
