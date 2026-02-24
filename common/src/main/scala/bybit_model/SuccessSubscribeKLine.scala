package bybit_model

import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

case class SuccessSubscribeKLine(
  success: Boolean,
  ret_msg: String,
  conn_id: String,
  op: String
)

object SuccessSubscribeKLine {
  implicit val encoder: JsonEncoder[SuccessSubscribeKLine] = DeriveJsonEncoder.gen[SuccessSubscribeKLine]
  implicit val decoder: JsonDecoder[SuccessSubscribeKLine] = DeriveJsonDecoder.gen[SuccessSubscribeKLine]
}
