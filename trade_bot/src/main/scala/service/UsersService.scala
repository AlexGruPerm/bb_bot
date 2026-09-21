package service

import bybit_model.Users
import conf.AppConfig
import zio.durationInt
import zio.{ Duration, Ref, UIO, ZEnvironment, ZIO, ZLayer }

import java.sql.{ SQLException, Timestamp }
import javax.sql.DataSource

trait UsersService {
  def getUsers: UIO[List[Users]]
  def activeUsers: UIO[List[Users]]
  def activeAdmins: UIO[List[Users]]
  def findUser(tgUserId: Long): UIO[Option[Users]]
  def refreshUsers: UIO[Unit]
}

final class UsersServiceLive(ref: Ref[List[Users]], db: DatabaseService, ds: DataSource) extends UsersService {

  override def getUsers: UIO[List[Users]] = ref.get

  override def activeUsers: UIO[List[Users]] =
    ref.get.map(_.filter(_.isActive(new Timestamp(System.currentTimeMillis()))))

  override def activeAdmins: UIO[List[Users]] =
    ref.get.map(_.filter(u => u.is_admin && u.isActive(new Timestamp(System.currentTimeMillis()))))

  override def findUser(tgUserId: Long): UIO[Option[Users]] =
    ref.get.map(_.find(_.user_id == tgUserId))

  override def refreshUsers: UIO[Unit] = UsersService.refreshFromDb(ref, db, ds)
}

object UsersService {

  private[service] def refreshFromDb(ref: Ref[List[Users]], db: DatabaseService, ds: DataSource): UIO[Unit] =
    db.getUsers
      .provideEnvironment(ZEnvironment(ds))
      .foldZIO(
        err => ZIO.logError(s"Failed to refresh users from DB: ${err.getMessage}"),
        users => ref.set(users) *> ZIO.logInfo(s"Users refreshed from DB, count = ${users.size}")
      )

  private def refreshLoop(
    ref: Ref[List[Users]],
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
