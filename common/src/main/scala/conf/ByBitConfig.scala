package conf

case class ByBitConfig(
  key: String,
  secret: String,
  limit_order_book: Int,
  interval_oi: String,
  limit_open_interest: Int,
  recvWindow: String,
  pp_check_freq_sec: Int,
  pp_check_restart: Int,
  check_restart_savebar_freq: Int,
  save_order_book_freq_mins: Int
) {
  override def toString: String =
    s"""
       |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       | ByBit:
       | key      : ******
       | secret   : ******
       |""".stripMargin
}
