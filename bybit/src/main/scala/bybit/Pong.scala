package bybit

import zio.json.{ DeriveJsonCodec, JsonCodec }

final case class Pong(
  success: Boolean,
  ret_msg: String,
  conn_id: String,
  op: String
)

object Pong {
  implicit val codec: JsonCodec[Pong] = DeriveJsonCodec.gen
}
