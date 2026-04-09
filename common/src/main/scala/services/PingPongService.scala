package services

import bybit_model.Types.IntervalCode
import bybit_model._
import zio.{ Clock, Fiber, IO, Ref, UIO, ZIO, ZLayer }

import java.util.concurrent.TimeUnit
import javax.sql.DataSource

trait PingPongService {
  def getKeysIntervals: UIO[Set[IntervalCode]]
  def initEmptyPingPong(c_interval: IntervalCode, fiber: Fiber[Throwable, Unit]): UIO[Unit]
  def interruptFiberRemove(c_interval: IntervalCode): UIO[Unit]
  def remove(c_interval: IntervalCode): UIO[Unit]
  def saveNeedRestart(c_interval: IntervalCode): UIO[Unit]
  def get(c_interval: IntervalCode): UIO[Option[PingPong]]
  def savePing(c_interval: IntervalCode, ping_ts: Long): UIO[Unit]
  def savePong(c_interval: IntervalCode, pong_ts: Long): UIO[Unit]
  def saveState(c_interval: IntervalCode, state: PingPongState): UIO[Unit]
  def check(c_interval: IntervalCode, ppCheckInterval: Int): IO[Nothing, Unit]
  def checkAllIntervals(ppCheckInterval: Int): IO[Nothing, Unit]
  def getIntervalForRestart: UIO[Option[IntervalCode]]
  def getIntervalsForRestart: UIO[Set[IntervalCode]]
}

case class PingPongServiceImpl(ref: Ref[Map[IntervalCode, PingPong]]) extends PingPongService {

  override def getKeysIntervals: UIO[Set[IntervalCode]] =
    ref.get.map(_.keys.toSet)

  override def initEmptyPingPong(c_interval: IntervalCode, fiber: Fiber[Throwable, Unit]): UIO[Unit] = for {
    now <- Clock.currentTime(TimeUnit.SECONDS)
    _   <- ref.update(
      _.updated(c_interval, PingPong(c_interval = c_interval, fiber = Some(fiber), emptyOrLastPingSaved = now))
    )
  } yield ()

  override def remove(c_interval: IntervalCode): UIO[Unit] = ref.update(_.removed(c_interval))

  override def saveNeedRestart(c_interval: IntervalCode): UIO[Unit] = for {
    now <- Clock.currentTime(TimeUnit.SECONDS)
    _   <- ZIO.logInfo(s"[REF] SAVE NEED RESTART int = $c_interval")
    _   <- ref.update(_.updatedWith(c_interval) {
      case Some(pp) =>
        Some(
          pp.copy(ping_ts = 0L, pong_ts = 0L, state = Ok, fiber = None, emptyOrLastPingSaved = now, needRestart = true)
        )
      case None     => None
    })
  } yield ()

  override def interruptFiberRemove(c_interval: IntervalCode): UIO[Unit] = for {
    pp <- get(c_interval)
    _  <- pp match {
      case Some(pingPong) if pingPong.fiber.isDefined =>
        val fiberToInterrupt = pingPong.fiber.get
        ZIO.logInfo(s"PP INTERRUPT [fibId = ${pingPong.getFiberId}]") *>
          fiberToInterrupt.interrupt
      case _                                          => ZIO.unit
    }
    _  <- saveNeedRestart(c_interval)
    _  <- ZIO.logInfo(s"PP INTERRUPTED")
  } yield ()

  override def get(c_interval: IntervalCode): UIO[Option[PingPong]] = ref.get.map(m => m.get(c_interval))

  override def savePing(c_interval: IntervalCode, ping_ts: Long): UIO[Unit] = for {
    now <- Clock.currentTime(TimeUnit.SECONDS)
    _   <- ref.update(_.updatedWith(c_interval) {
      case Some(pp) => Some(pp.copy(ping_ts = ping_ts, pong_ts = 0L, state = Ok, emptyOrLastPingSaved = now))
      case None     => Some(PingPong(c_interval = c_interval, ping_ts = ping_ts, emptyOrLastPingSaved = now))
    })
  } yield ()

  override def savePong(c_interval: IntervalCode, pong_ts: Long): UIO[Unit] =
    ref.update(_.updatedWith(c_interval) {
      case Some(pp) => Some(pp.copy(pong_ts = pong_ts))
      case None     => None
    })

  override def saveState(c_interval: IntervalCode, state: PingPongState): UIO[Unit] = for {
    pp          <- get(c_interval)
    stateChanged = pp.exists(_.state != state)
    _           <- ref
      .update(_.updatedWith(c_interval) {
        case Some(pp) => Some(pp.copy(state = state))
        case None     => None
      })
      .when(stateChanged)
  } yield ()

  override def check(c_interval: IntervalCode, ppCheckInterval: Int): IO[Nothing, Unit] = for {
    ppOpt <- get(c_interval)
    now   <- Clock.currentTime(TimeUnit.SECONDS)
    _     <- ppOpt match {
      case Some(pp) =>
        val diffSeconds = pp.getDiff(now)
        ZIO.when(!pp.needRestart && diffSeconds > ppCheckInterval)(
          ZIO.logInfo(
            s"PP ERROR [fibId = ${pp.getFiberId}][${pp.state.getName}][int = ${pp.c_interval}]: " +
              s"ppDiff = $diffSeconds ping:${pp.ping_ts} pong:${pp.pong_ts} now:$now "
          ) *>
            saveState(c_interval, Timeout) *> interruptFiberRemove(c_interval)
        ) *>
          ZIO.when(!pp.needRestart && diffSeconds <= 8 && pp.getLastPingSecBack(now) >= 30)(
            ZIO.logInfo(s"PP POS. NO INTERNET [fibId = ${pp.getFiberId}][empt = ${pp.getLastPingSecBack(now)}]") *>
              saveState(c_interval, Timeout) *> interruptFiberRemove(c_interval)
          )
      case None     => ZIO.unit
    }
  } yield ()

  override def checkAllIntervals(ppCheckInterval: Int): IO[Nothing, Unit] = for {
    intervals <- ref.get.map(_.keys)
    _         <- ZIO.foreach(intervals)(i => check(i, ppCheckInterval))
  } yield ()

  override def getIntervalForRestart: UIO[Option[IntervalCode]] =
    ref.get.map {
      _.find(_._2.needRestart == true).map(_._1)
    }

  def getIntervalsForRestart: UIO[Set[IntervalCode]] =
    ref.get.map {
      _.filter(_._2.needRestart == true).keySet
    }
}

object PingPongService {
  def layer: ZLayer[Any, Nothing, PingPongService] =
    ZLayer.fromZIO(Ref.make(Map.empty[IntervalCode, PingPong]).map(r => PingPongServiceImpl(r)))
}
