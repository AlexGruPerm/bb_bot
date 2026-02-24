package bybit_model

import java.sql.Timestamp

case class CandleInsert(
  id: Long,
  id_symbol: Int,
  start_ts: Option[Long],
  end_ts: Long,
  c_interval: String,
  o: Double,
  h: Double,
  l: Double,
  c: Double,
  v: Double
)

case class InsertedCandle(
  id: Long,
  id_symbol: Int,
  ts_db: java.sql.Timestamp,
  start_ts: Option[Long],
  end_ts: Long,
  c_interval: String,
  o: Double,
  h: Double,
  l: Double,
  c: Double,
  v: Double
)
