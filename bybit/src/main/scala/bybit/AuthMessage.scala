package bybit

import zio.json.{ DeriveJsonEncoder, JsonEncoder }

final case class AuthMessage(req_id: String, op: String, args: Array[String])

object AuthMessage {
  implicit val authMessageEncoder: JsonEncoder[AuthMessage] = DeriveJsonEncoder.gen[AuthMessage]
}
