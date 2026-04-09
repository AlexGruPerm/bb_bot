package bybit_model

import bybit_model.Types.IntervalCode
import zio.Fiber

case class PingPong(
  c_interval: IntervalCode,
  ping_ts: Long = 0L,
  pong_ts: Long = 0L,
  state: PingPongState = Ok,
  emptyOrLastPingSaved: Long = 0, // accumulated time while ping_ts = 0L
  fiber: Option[Fiber[Throwable, Unit]] = None,
  needRestart: Boolean = false
) {
  def getFiberId: Int =
    fiber.map(_.id.ids.headOption.getOrElse(0)).getOrElse(0)

  def getLastPingSecBack(now: Long): Long =
    now - emptyOrLastPingSaved

  def getDiff(now: Long): Long =
    if (ping_ts == 0)
      0L
    else {
      if (pong_ts != 0L)
        pong_ts - ping_ts
      else
        now - ping_ts
    }

  override def toString: String =
    s"int[$c_interval] ping[$ping_ts] ping[$pong_ts] fibID[${getFiberId}] needRestart[$needRestart]"

}
