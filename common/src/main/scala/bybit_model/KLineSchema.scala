package bybit_model

case class KLineInsert(
  id_candle: Long,
  start_ts: Long,
  end_ts: Long,
  o: Double,
  h: Double,
  l: Double,
  c: Double,
  v: Double
)
