package bybit_model

case class PingPongException(msg: String) extends Exception {
  override def getMessage: String = msg
}
