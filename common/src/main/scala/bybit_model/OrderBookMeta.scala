package bybit_model

import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

case class OrderBookEntry(price: Double, amount: Double)

object OrderBookEntry {
  implicit val decoder: JsonDecoder[OrderBookEntry] =
    JsonDecoder.tuple2[String, String].map { case (priceStr, amountStr) =>
      OrderBookEntry(priceStr.toDouble, amountStr.toDouble)
    }

  implicit val encoder: JsonEncoder[OrderBookEntry] = JsonEncoder.tuple2[String, String].contramap { entry =>
    (entry.price.toString, entry.amount.toString)
  }
}

case class OrderBookResult(
  a: List[OrderBookEntry],
  b: List[OrderBookEntry],
  ts: Long
)

object OrderBookResult {
  implicit val encoder: JsonEncoder[OrderBookResult] = DeriveJsonEncoder.gen[OrderBookResult]
  implicit val decoder: JsonDecoder[OrderBookResult] = DeriveJsonDecoder.gen[OrderBookResult]
}

case class ApiRespOrderBook(
  retCode: Int,
  retMsg: String,
  result: OrderBookResult,
  retExtInfo: Map[String, String],
  time: Long
)

object ApiRespOrderBook {
  implicit val encoder: JsonEncoder[ApiRespOrderBook] = DeriveJsonEncoder.gen[ApiRespOrderBook]
  implicit val decoder: JsonDecoder[ApiRespOrderBook] = DeriveJsonDecoder.gen[ApiRespOrderBook]
}
