package bybit_model

import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

case class ApiRespOrderHistInfo(
  retCode: Int,
  retMsg: String,
  result: OrderHistResult,
  retExtInfo: Map[String, String],
  time: Long
)

object ApiRespOrderHistInfo {
  implicit val encoder: JsonEncoder[ApiRespOrderHistInfo] = DeriveJsonEncoder.gen[ApiRespOrderHistInfo]
  implicit val decoder: JsonDecoder[ApiRespOrderHistInfo] = DeriveJsonDecoder.gen[ApiRespOrderHistInfo]
}

case class OrderHistResult(
  nextPageCursor: String,
  category: String,
  list: List[OrderItemInfo]
)

object OrderHistResult {
  implicit val encoder: JsonEncoder[OrderHistResult] = DeriveJsonEncoder.gen[OrderHistResult]
  implicit val decoder: JsonDecoder[OrderHistResult] = DeriveJsonDecoder.gen[OrderHistResult]
}

case class OrderItemInfo(
  orderId: String,
  orderLinkId: String,
  blockTradeId: String,
  symbol: String,
  price: String,
  qty: String,
  side: String,
  isLeverage: String,
  orderStatus: String,
  cancelType: String,
  rejectReason: String,
  avgPrice: String,
  cumExecFee: String,
  slippageToleranceType: String,
  marketUnit: String,
  slippageTolerance: String,
  extraFees: Option[String]
) {
  def toOrderItemInfoInsert(): OrderItemInfoInsert =
    OrderItemInfoInsert(
      orderId.toLong,
      orderLinkId.toLong,
      symbol,
      price.toDouble,
      qty.toDouble,
      side,
      isLeverage.toInt,
      orderStatus,
      cancelType,
      rejectReason,
      avgPrice.toDouble,
      cumExecFee.toDouble,
      slippageToleranceType,
      marketUnit,
      slippageTolerance.toDouble
    )

}

object OrderItemInfo {
  implicit val encoder: JsonEncoder[OrderItemInfo] = DeriveJsonEncoder.gen[OrderItemInfo]
  implicit val decoder: JsonDecoder[OrderItemInfo] = DeriveJsonDecoder.gen[OrderItemInfo]
}

case class OrderItemInfoInsert(
  orderId: Long,
  orderLinkId: Long,
  symbol: String,
  price: Double,
  qty: Double,
  side: String,
  isLeverage: Int,
  orderStatus: String,
  cancelType: String,
  rejectReason: String,
  avgPrice: Double,
  cumExecFee: Double,
  slippageToleranceType: String,
  marketUnit: String,
  slippageTolerance: Double
)
