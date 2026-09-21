package service

import conf.AppConfig
import zio.durationInt
import zio.{ Duration, Ref, UIO, ZEnvironment, ZIO, ZLayer }

import java.sql.{ SQLException, Timestamp }
import javax.sql.DataSource

trait UsersService {
  def refreshUsers: UIO[Unit]
}



    ref.get.map(_.filter(_.isActive(new Timestamp(System.currentTimeMillis()))))

    ref.get.map(_.filter(u => u.is_admin && u.isActive(new Timestamp(System.currentTimeMillis()))))

    ref.get.map(_.find(_.user_id == tgUserId))

  override def refreshUsers: UIO[Unit] = UsersService.refreshFromDb(ref, db, ds)
}

object UsersService {

    db.getUsers
      .provideEnvironment(ZEnvironment(ds))
      .foldZIO(
        err => ZIO.logError(s"Failed to refresh users from DB: ${err.getMessage}"),
        users => ref.set(users) *> ZIO.logInfo(s"Users refreshed from DB, count = ${users.size}")
      )

  private def refreshLoop(
    db: DatabaseService,
    ds: DataSource,
    interval: Duration
  ): UIO[Nothing] =
    (ZIO.sleep(interval) *> refreshFromDb(ref, db, ds)).forever

  val live: ZLayer[AppConfig with DatabaseService with DataSource, Throwable, UsersService] =
    ZLayer.scoped {
      for {
        conf    <- ZIO.service[AppConfig]
        db      <- ZIO.service[DatabaseService]
        ds      <- ZIO.service[DataSource]
        initial <- db
          .getUsers
          .provideEnvironment(ZEnvironment(ds))
          .mapError(e => new SQLException(s"Failed to load users from DB on startup: ${e.getMessage}", e))
        _       <- ZIO.logInfo(s"Users loaded from DB on startup, count = ${initial.size}")
        ref     <- Ref.make(initial)
        interval = conf.telegram.usersRefreshMins.minutes
        _       <- refreshLoop(ref, db, ds, interval).forkScoped
      } yield new UsersServiceLive(ref, db, ds)
    }
}
