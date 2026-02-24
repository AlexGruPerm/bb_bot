package bybit_model

import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

case class KLineEntity(
  start: Long,
  end: Long,
  interval: String,
  open: Double,
  close: Double,
  high: Double,
  low: Double,
  volume: Double,
  turnover: Double,
  confirm: Boolean
  // timestamp: Long
) {
  def toCandleInsert(symbolId: Int): CandleInsert =
    CandleInsert(
      id = 0L,
      id_symbol = symbolId,
      start_ts = Some(start),
      end_ts = end,
      c_interval = interval,
      o = open,
      h = high,
      l = low,
      c = close,
      v = volume
    )

  def toKLineInsert(idCandle: Long): KLineInsert =
    KLineInsert(
      id_candle = idCandle,
      start_ts = start,
      end_ts = end,
      o = open,
      h = high,
      l = low,
      c = close,
      v = volume
    )

}

object KLineEntity {
  implicit val encoder: JsonEncoder[KLineEntity] = DeriveJsonEncoder.gen[KLineEntity]
  implicit val decoder: JsonDecoder[KLineEntity] = DeriveJsonDecoder.gen[KLineEntity]
}

case class KLine(
  topic: String,
  data: KLineEntity
)

object KLine {
  implicit val decoder: JsonDecoder[KLine] =
    DeriveJsonDecoder.gen[TempKLine].map { tmp =>
      KLine(
        topic = tmp.topic,
        // data contains single element, so we can use headOption
        data = tmp
          .data
          .headOption
          .getOrElse(
            throw new RuntimeException("data array empty")
          )
      )
    }

  private case class TempKLine(
    topic: String,
    data: List[KLineEntity]
  )

  implicit val encoder: JsonEncoder[KLine] = DeriveJsonEncoder.gen[KLine]
}
