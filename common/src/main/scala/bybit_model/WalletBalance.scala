package bybit_model

import bybit_model.Types.CoinId
import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

case class ApiRespWalletBalance(
  retCode: Int,
  retMsg: String,
  result: WalletBalanceResult,
  retExtInfo: Map[String, String],
  time: Long
)

case class WalletBalanceResult(balance: WalletBalanceEntity)

case class CommonWalletBalance(
  totalequity: Double,
  is_actual: Boolean,
  diff_seconds: Long,
  ts_current: String,
  ts_bybit: String
)

case class SymbolsBalance(
  symbol_id: Option[Int],
  symbol_code: Option[String],
  coin_code: String,
  is_enabled: Option[Boolean],
  equity: Double,
  usdvalue: Double
)

case class WalletBalanceEntity(
  totalEquity: Double,
  totalInitialMargin: Double,
  totalAvailableBalance: Double,
  totalWalletBalance: Double,
  coin: List[WalletBalanceEntityCoin]
) {
  def toWalletBalanceInsert(ts_bybit: Long): WalletBalanceInsert =
    WalletBalanceInsert(
      0L,
      totalEquity,
      totalInitialMargin,
      totalAvailableBalance,
      totalWalletBalance,
      ts_bybit
    )
}

case class WalletBalanceEntityCoin(
  equity: Double,
  usdValue: Double,
  borrowAmount: Double,
  walletBalance: Double,
  cumRealisedPnl: Double,
  coin: String
) {
  def toWalletBalanceCoinInsert(id_wallet_balance: Long, id_coin: Option[CoinId]): WalletBalanceCoinInsert =
    id_coin match {
      case Some(idCoin) =>
        WalletBalanceCoinInsert(
          id_wallet_balance,
          idCoin,
          equity,
          usdValue,
          cumRealisedPnl
        )
      case None         => throw new Exception("toWalletBalanceCoinInsert id_coin is None.")
    }

}

object ApiRespWalletBalance {
  implicit val encoder: JsonEncoder[ApiRespWalletBalance] = DeriveJsonEncoder.gen[ApiRespWalletBalance]
  implicit val decoder: JsonDecoder[ApiRespWalletBalance] = DeriveJsonDecoder.gen[ApiRespWalletBalance]
}

object WalletBalanceResult {
  private case class RawWalletBalanceResult(
    list: List[WalletBalanceEntity]
  )

  implicit val decoder: JsonDecoder[WalletBalanceResult] =
    DeriveJsonDecoder.gen[RawWalletBalanceResult].map { raw =>
      val first = raw
        .list
        .headOption
        .getOrElse(
          throw new RuntimeException("WalletBalanceResult.list is empty")
        )
      WalletBalanceResult(first)
    }

  implicit val encoder: JsonEncoder[WalletBalanceResult] = DeriveJsonEncoder.gen[WalletBalanceResult]
}

object WalletBalanceEntity {
  implicit val decoder: JsonDecoder[WalletBalanceEntity] = DeriveJsonDecoder.gen[WalletBalanceEntity]
  implicit val encoder: JsonEncoder[WalletBalanceEntity] = DeriveJsonEncoder.gen[WalletBalanceEntity]
}

object WalletBalanceEntityCoin {
  implicit val decoder: JsonDecoder[WalletBalanceEntityCoin] = DeriveJsonDecoder.gen[WalletBalanceEntityCoin]
  implicit val encoder: JsonEncoder[WalletBalanceEntityCoin] = DeriveJsonEncoder.gen[WalletBalanceEntityCoin]
}

case class WalletBalanceInsert(
  id: Long,
  totalEquity: Double,
  totalInitialMargin: Double,
  totalAvailableBalance: Double,
  totalWalletBalance: Double,
  ts_bybit: Long
)

case class WalletBalanceCoinInsert(
  id_wallet_balance: Long,
  id_coin: CoinId,
  equity: Double,
  usdValue: Double,
  cumRealisedPnl: Double
)
