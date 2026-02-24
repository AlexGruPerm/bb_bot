package bybit_model

trait PingPongState {
  def getName: String
}
object Ok      extends PingPongState {
  override def getName: String = "Ok"
}
object Timeout extends PingPongState {
  override def getName: String = "Timeout"
}
