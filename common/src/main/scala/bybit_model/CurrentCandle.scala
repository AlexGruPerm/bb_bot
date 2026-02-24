package bybit_model

/**
 * Contain current Candle from data.candle that using for inserts intermediate Candles in data.kline. (field
 * data.kline.id_candle) As a Ref[Map[String,CurrentCandle]] Map key contains value "kline.1.SOLUSDT" from
 * "topic":"kline.1.SOLUSDT" ByBitService.getAndSaveBars
 */
case class CurrentCandle(id_candle: Long = 0L, interval: String)
