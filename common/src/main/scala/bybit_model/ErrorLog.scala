package bybit_model

import bybit_model.Types.LogLevelId

case class ErrorLog(
  id_loglevel: LogLevelId,
  bb_module: String,
  bb_action: String,
  error_class: String,
  msg: String
)
