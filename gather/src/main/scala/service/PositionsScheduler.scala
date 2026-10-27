package service

import app.{ CommonGatherPosEnv, ErrorHandlers, GatherDataSourceEnv }
import bybit_model.{ ErrorLog, FuturesPositionsConfig }
import postgresql.{ DictChanges, DictEvent }
import services.LogLevelService
import zio.{ durationInt, Fiber, Ref, Schedule, ZIO, ZLayer }

import java.sql.SQLException
import javax.sql.DataSource

/**
 * Collects futures positions of the trading account.
 *
 * Two independent effects are kept running while data.futures_positions_config.is_enabled is true:
 *   - an open-positions snapshot every `open_poll_secs` seconds (appended to data.futures_position);
 *   - a closed-pnl poll every `closed_poll_secs` seconds, querying the last `closed_lookback_min` minutes (deduplicated
 *     in data.futures_position_closed).
 *
 * Any change of the config table (bb_dict_changed notification) reloads the config: running fibers are interrupted and
 * restarted with the new periods. When is_enabled is false both fibers stay stopped until the next notification.
 */
trait PositionsScheduler {
  def start: ZIO[CommonGatherPosEnv, Nothing, Unit]
}

final class PositionsSchedulerLive(db: DatabaseService, dc: DictChanges) extends PositionsScheduler {

  import PositionsScheduler._

  private val changeTables = Set("futures_positions_config")

  private def loadConfig: ZIO[DataSource, SQLException, Option[FuturesPositionsConfig]] =
    db.getFuturesPositionsConfig

  private def savePositions: ZIO[CommonGatherPosEnv, Throwable, Unit] =
    ZIO.serviceWithZIO[GatherService](_.saveFuturesPositions())

  private def saveClosed(lookbackMin: Int): ZIO[CommonGatherPosEnv, Throwable, Unit] =
    ZIO.serviceWithZIO[GatherService](_.saveFuturesPositionsClosed(lookbackMin))

  /** Allows ErrorHandlers.logPF to persist a failure into common_log, like the other gather collectors do. */
  private implicit val funcSaveLogDb: ErrorLog => ZIO[GatherDataSourceEnv, SQLException, Unit] = err =>
    ZIO.serviceWithZIO[GatherService](_.saveErrorInDb(err))

  /**
   * Wraps one poll so that a failure is stored in common_log and the loop keeps running instead of dying.
   */
  private def guarded(
    action: String,
    poll: ZIO[CommonGatherPosEnv, Throwable, Unit]
  ): ZIO[CommonGatherPosEnv, Nothing, Unit] =
    ZIO.logDebug(s"futures positions: $action started") *>
      (for {
        logLevelId <- ZIO.serviceWithZIO[LogLevelService](_.findByCode("error"))
        _          <- poll.catchSome(ErrorHandlers.logPF(logLevelId.id, module, action))
      } yield ()).catchAll(e => ZIO.logError(s"futures positions ($action) failed: ${e.getMessage}"))

  private def scheduledOpen(config: FuturesPositionsConfig): ZIO[CommonGatherPosEnv, Nothing, Unit] =
    guarded("saveFuturesPositions", savePositions).repeat(Schedule.spaced(config.openPollSecs.seconds)).unit

  private def scheduledClosed(config: FuturesPositionsConfig): ZIO[CommonGatherPosEnv, Nothing, Unit] =
    guarded("saveFuturesPositionsClosed", saveClosed(config.closedLookbackMin))
      .repeat(Schedule.spaced(config.closedPollSecs.seconds))
      .unit

  private def stopRunning(running: Ref[Set[Fiber.Runtime[Any, Unit]]]): ZIO[Any, Nothing, Unit] =
    running.get.flatMap(fs => ZIO.foreachDiscard(fs)(_.interrupt)).ignore *> running.set(Set.empty)

  private def restart(running: Ref[Set[Fiber.Runtime[Any, Unit]]]): ZIO[CommonGatherPosEnv, Nothing, Unit] =
    for {
      _      <- ZIO.logInfo("(Re)loading futures positions configuration")
      config <- loadConfig.catchAll { e =>
        ZIO.logError(s"Cannot load futures_positions_config: ${e.getMessage}").as(None)
      }
      _      <- stopRunning(running)
      _      <- config match {
        case Some(c) if c.isEnabled =>
          for {
            fibers <- ZIO.foreach(List(scheduledOpen(c), scheduledClosed(c)))(_.forkDaemon)
            _      <- running.set(fibers.toSet)
            _      <- ZIO.logInfo(
              s"Futures positions scheduler: open every ${c.openPollSecs}s, closed every ${c.closedPollSecs}s " +
                s"(lookback ${c.closedLookbackMin}m)"
            )
          } yield ()
        case Some(_)                =>
          ZIO.logInfo("Futures positions scheduler: disabled by config (is_enabled = false)")
        case None                   =>
          ZIO.logWarning("Futures positions scheduler: data.futures_positions_config is empty, not started")
      }
    } yield ()

  private def reloadOnChange(running: Ref[Set[Fiber.Runtime[Any, Unit]]]): ZIO[CommonGatherPosEnv, Nothing, Unit] =
    dc.events
      .collect {
        case DictEvent.TableChanged(table) if changeTables.contains(table) => ()
        case DictEvent.Resync                                              => ()
      }
      .groupedWithin(Int.MaxValue, 3.seconds)
      .foreach(chunk => if (chunk.isEmpty) ZIO.unit else restart(running))

  override def start: ZIO[CommonGatherPosEnv, Nothing, Unit] =
    for {
      _       <- ZIO.logInfo("Starting futures positions scheduler")
      running <- Ref.make[Set[Fiber.Runtime[Any, Unit]]](Set.empty)
      _       <- restart(running)
      _       <- reloadOnChange(running).forkDaemon
    } yield ()
}

object PositionsScheduler {

  val module: String = "PositionsScheduler"

  val live: ZLayer[DatabaseService with DictChanges, Nothing, PositionsScheduler] =
    ZLayer.fromFunction(new PositionsSchedulerLive(_, _))
}
