package bybit

sealed trait ByBitEndpoint
case object OrderBook    extends ByBitEndpoint
case object OpenInterest extends ByBitEndpoint
