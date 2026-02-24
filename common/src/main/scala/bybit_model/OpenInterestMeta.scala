package bybit_model

import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

case class OpenInterestEntry(openInterest: Double, timestamp: Long)

object OpenInterestEntry {
  implicit val encoder: JsonEncoder[OpenInterestEntry] = DeriveJsonEncoder.gen[OpenInterestEntry]
  implicit val decoder: JsonDecoder[OpenInterestEntry] = DeriveJsonDecoder.gen[OpenInterestEntry]
}

case class OpenInterestResult(
  symbol: String,
  category: String,
  list: List[OpenInterestEntry]
)

object OpenInterestResult {
  implicit val encoder: JsonEncoder[OpenInterestResult] = DeriveJsonEncoder.gen[OpenInterestResult]
  implicit val decoder: JsonDecoder[OpenInterestResult] = DeriveJsonDecoder.gen[OpenInterestResult]
}

case class ApiRespOpenInterest(
  retCode: Int,
  retMsg: String,
  result: OpenInterestResult,
  retExtInfo: Map[String, String],
  time: Long
)

object ApiRespOpenInterest {
  implicit val encoder: JsonEncoder[ApiRespOpenInterest] = DeriveJsonEncoder.gen[ApiRespOpenInterest]
  implicit val decoder: JsonDecoder[ApiRespOpenInterest] = DeriveJsonDecoder.gen[ApiRespOpenInterest]
}
