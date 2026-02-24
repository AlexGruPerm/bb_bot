package bybit_model

case class AdviceToUser(
  adviser_id: Int,
  advice_description: String,
  proc: String,
  advice_id: Int,
  id_candle: Long,
  start_ts: Long,
  id_symbol: Int,
  symbol_code: String,
  ts_db: java.sql.Timestamp,
  c_interval: String,
  last_price: Double,
  advice: String
)
