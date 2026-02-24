package bybit

import zio.json.{ DeriveJsonEncoder, JsonEncoder }

final case class SubscribeCoins(op: String, args: Set[String])

object SubscribeCoins {
  implicit val SubscribeCoinsEncoder: JsonEncoder[SubscribeCoins] = DeriveJsonEncoder.gen[SubscribeCoins]
}
