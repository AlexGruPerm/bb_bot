package service

import bybit_model.{ FuturesMetrics, FuturesMetricsMeta }
import postgresql.{ DictChanges, DictEvent }
import zio.{ durationInt, Fiber, Ref, Schedule, ZIO, ZLayer }

import java.sql.SQLException
import javax.sql.DataSource

trait FuturesMetricsScheduler {
  def start: ZIO[DataSource, Nothing, Unit]
}

/**
 * Loads futures metrics configuration (data.futures_metrics + data.futures_metrics_meta) at application start and runs
 * the analysis queries once per (metric, meta) pair, repeating every `meta.executeEveryMins` minutes. Listens for
 * bb_dict_changed notifications on either configuration table; on any change it reloads the whole config, interrupts
 * the running scheduled effects and restarts them with the fresh parameters and frequency.
 */
final class FuturesMetricsSchedulerLive(db: DatabaseService, dc: DictChanges) extends FuturesMetricsScheduler {

  private[this] val changeTables   = Set("futures_metrics", "futures_metrics_meta")
  private[this] val ReloadDebounce = 3.seconds

  private def loadPairs: ZIO[DataSource, SQLException, List[(FuturesMetrics, FuturesMetricsMeta)]] = for {
    metrics <- db.getFuturesMetrics
    metas   <- db.getFuturesMetricsMeta
    _       <- ZIO.foreachDiscard(metrics) { m =>
      ZIO.foreachDiscard(metas.filter(_.idFuturesMetrics == m.id)) { mt =>
        ZIO.logInfo(
          s"metric = ${m.metricName}  executeEveryMins = ${mt.executeEveryMins} filterWindowMin = ${mt.filterWindowMin}"
        )
      }
    }
  } yield {
    val metricsById = metrics.map(m => m.id -> m).toMap
    // a metric without a meta configuration is intentionally not scheduled
    metas.flatMap(meta => metricsById.get(meta.idFuturesMetrics).map(metric => metric -> meta))
  }

  /**
   * One scheduled effect per pair: run immediately, then repeat every execute_every_mins. A failure is logged and the
   * loop keeps going; only an external interrupt (restart) stops it.
   */
  private def scheduled(pair: (FuturesMetrics, FuturesMetricsMeta)): ZIO[DataSource, Nothing, Unit] = {
    val (metric, meta) = pair
    (for {
      _ <- db.executeFuturesMetricsAnalysis(metric, meta)
    } yield ())
      .catchAll(e => ZIO.logError(s"Futures metric run failed: ${e.getMessage}"))
      .repeat(Schedule.spaced(meta.executeEveryMins.minutes))
      .unit
  }

  /**
   * Stop the currently running scheduled effects, re-read the config and spawn new ones. Used both at initial load and
   * on every config-change notification.
   */
  private def restart(running: Ref[Set[Fiber.Runtime[Any, Unit]]]): ZIO[DataSource, Nothing, Unit] =
    for {
      _      <- ZIO.logInfo("(Re)loading futures metrics configuration")
      pairs  <- loadPairs.catchAll { e =>
        ZIO.logError(s"Cannot load futures metrics config: ${e.getMessage}") *> ZIO.succeed(List.empty)
      }
      _      <- running.get.flatMap(fs => ZIO.foreachDiscard(fs)(_.interrupt)).ignore
      _      <- running.set(Set.empty)
      fibers <- ZIO.foreach(pairs) { pair =>
        scheduled(pair).fork
      }
      _      <- running.set(fibers.toSet)
      _      <- ZIO.logInfo(s"Futures metrics scheduler: ${pairs.size} effect(s) running")
    } yield ()

  /** Reload once after a short debounce, coalescing bursts of notifications on the two config tables. */
  private def reloadOnChange(running: Ref[Set[Fiber.Runtime[Any, Unit]]]): ZIO[DataSource, Nothing, Unit] =
    dc.events
      .collect {
        case DictEvent.TableChanged(table) if changeTables.contains(table) => ()
        case DictEvent.Resync                                              => ()
      }
      .groupedWithin(Int.MaxValue, ReloadDebounce)
      .foreach(chunk => if (chunk.isEmpty) ZIO.unit else restart(running))

  override def start: ZIO[DataSource, Nothing, Unit] =
    for {
      _       <- ZIO.logInfo("Starting futures metrics scheduler")
      running <- Ref.make[Set[Fiber.Runtime[Any, Unit]]](Set.empty)
      _       <- restart(running)
      _       <- reloadOnChange(running).forkDaemon
    } yield ()

}

object FuturesMetricsScheduler {
  val live: ZLayer[DatabaseService with DictChanges, Nothing, FuturesMetricsScheduler] =
    ZLayer.fromFunction(new FuturesMetricsSchedulerLive(_, _))
}
