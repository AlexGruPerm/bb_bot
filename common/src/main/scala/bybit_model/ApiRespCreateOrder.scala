package bybit_model

import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

case class ApiRespCreateOrder(
  retCode: Int,
  retMsg: String,
  result: CreateOrderResult,
  retExtInfo: Map[String, String],
  time: Long
)

case class CreateOrderResult(
  orderId: Long,
  orderLinkId: String
)

case class createdOrder(
  orderId: Long,
  time: Long
)

object createdOrder {
  def getEmpty: createdOrder =
    createdOrder(0L, 0L)
}

object CreateOrderResult {
  implicit val encoder: JsonEncoder[CreateOrderResult] = DeriveJsonEncoder.gen[CreateOrderResult]
  implicit val decoder: JsonDecoder[CreateOrderResult] = DeriveJsonDecoder.gen[CreateOrderResult]
}

object ApiRespCreateOrder {
  implicit val encoder: JsonEncoder[ApiRespCreateOrder] = DeriveJsonEncoder.gen[ApiRespCreateOrder]
  implicit val decoder: JsonDecoder[ApiRespCreateOrder] = DeriveJsonDecoder.gen[ApiRespCreateOrder]
}
