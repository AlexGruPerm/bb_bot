package bybit_model

import bybit_model.Types.{ IntervalCode, IntervalId, SymbolId }

case class RefSymbolsIntervals(
  id_symbol: SymbolId,
  id_interval: IntervalId,
  kline_topic: String,
  c_interval: IntervalCode
)
