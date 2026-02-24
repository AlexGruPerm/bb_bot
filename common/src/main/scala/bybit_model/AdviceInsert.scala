package bybit_model

case class AdviceInsert(
  id: Int,
  adviser_id: Int,
  id_candle: Long,
  id_symbol: Int,
  ts_db: java.sql.Timestamp,
  start_ts: Long,
  end_ts: Long,
  c_interval: String,
  o: Double,
  h: Double,
  l: Double,
  c: Double,
  v: Double,
  advice: Option[String],
  is_sent_to_user: Boolean
)
