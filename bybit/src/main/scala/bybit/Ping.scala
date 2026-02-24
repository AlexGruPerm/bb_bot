package bybit

import zio.json.{ DeriveJsonCodec, JsonCodec }

final case class Ping(op: String = "ping")
object Ping {
  implicit val codec: JsonCodec[Ping] = DeriveJsonCodec.gen
}
