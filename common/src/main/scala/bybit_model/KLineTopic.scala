package bybit_model

import bybit_model.Types.IntervalCode

final case class KLineTopic(symbol: Symbol, topic: String, intervalCode: IntervalCode) {
  def getTopicString: String = s"kline.$intervalCode.${symbol.code}"
}

object KLineTopic {
  // expect exactly this format "kline.{interval}.{symbol}" from bybit
  private val Pat = """^kline\.([^.]+)\.([^.]+)$""".r

  def parse(s: String, symbol: Symbol): KLineTopic =
    s match {
      case Pat(interval, _) => KLineTopic(symbol, "kline", interval)
      case _                => throw new Exception(s"Invalid KLine topic: $s")
    }

}
