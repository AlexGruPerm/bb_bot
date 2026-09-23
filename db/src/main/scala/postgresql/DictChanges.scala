package postgresql

import org.postgresql.PGConnection
import zio.durationInt
import zio.stream.ZStream
import zio.{ Hub, Scope, UIO, ZIO, ZLayer }

import java.sql.{ Connection, SQLException }
import javax.sql.DataSource

/** Event emitted by the PostgreSQL LISTEN/NOTIFY listener. */
sealed trait DictEvent
object DictEvent {

  /** A dictionary table was changed (payload = TG_TABLE_NAME). */
  final case class TableChanged(table: String) extends DictEvent

  /** Connection was (re)established - consume all dictionaries to resync. */
  case object Resync extends DictEvent
}

trait DictChanges {
  def events: ZStream[Any, Nothing, DictEvent]
}

object DictChanges {

  /** Channel used by migration triggers (see dock/migrations/changelog/dict_notify.xml). */
  val channel: String = "bb_dict_changed"

  private val PollEvery      = 200.millis
  private val reconnectDelay = 3.seconds

  private final class DictChangesLive(hub: Hub[DictEvent]) extends DictChanges {
    override def events: ZStream[Any, Nothing, DictEvent] =
      ZStream.unwrapScoped(hub.subscribe.map(queue => ZStream.fromQueue(queue)))
  }

  private def acquire(ds: DataSource): ZIO[Any, SQLException, Connection] =
    ZIO.attempt(ds.getConnection).refineToOrDie[SQLException]

  private def close(conn: Connection): UIO[Unit] =
    ZIO.attempt(conn.close()).orDie

  private def listen(conn: Connection): ZIO[Any, Throwable, Unit] =
    ZIO.attempt {
      val stmt = conn.createStatement()
      stmt.execute(s"LISTEN $channel")
      stmt.close()
    }

  private def drain(conn: Connection, hub: Hub[DictEvent]): ZIO[Any, Throwable, Any] =
    ZIO.attempt {
      Option(conn.unwrap(classOf[PGConnection]).getNotifications)
        .getOrElse(Array.empty)
        .toList
        .filter(_.getName == channel)
        .map(_.getParameter)
    }
      .flatMap(tables => ZIO.foreachDiscard(tables)(t => hub.publish(DictEvent.TableChanged(t))))

  private def pollLoop(conn: Connection, hub: Hub[DictEvent]): ZIO[Any, Throwable, Nothing] =
    (ZIO.sleep(PollEvery) *> drain(conn, hub)).forever

  /**
   * Opens a dedicated pooled connection, registers LISTEN and pumps notifications into the hub. Emits Resync on
   * (re)connect so consumers re-read all dictionaries (events that happened while the connection was down are lost
   * otherwise).
   */
  private def listenOnce(ds: DataSource, hub: Hub[DictEvent]): ZIO[Scope, Throwable, Nothing] =
    ZIO
      .acquireRelease(acquire(ds))(close)
      .flatMap { conn =>
        ZIO.logInfo(s"LISTEN '$channel' established") *>
          listen(conn) *>
          hub.publish(DictEvent.Resync) *>
          pollLoop(conn, hub)
      }

  /** Loops forever: reconnect with a backoff after the connection dies. */
  private def supervisor(ds: DataSource, hub: Hub[DictEvent]): UIO[Nothing] =
    (ZIO
      .scoped(listenOnce(ds, hub).unit)
      .foldZIO(
        err =>
          ZIO.logError(s"DictChanges listener failed: ${err.getMessage}; reconnect in $reconnectDelay") *> ZIO
            .sleep(reconnectDelay),
        _ => ZIO.sleep(reconnectDelay)
      ))
      .forever

  val live: ZLayer[DataSource, Throwable, DictChanges] =
    ZLayer.scoped {
      for {
        ds  <- ZIO.service[DataSource]
        hub <- Hub.unbounded[DictEvent]
        _   <- supervisor(ds, hub).forkScoped
      } yield new DictChangesLive(hub)
    }
}
