package bybit_model

/** An admin alert row from data.common_log ready to be reported to Telegram. */
final case class AdminAlert(
  id: Long,
  readable_diff: String,
  module_action: String,
  msg: String
)
