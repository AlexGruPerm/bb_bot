package service

import app.{ ZioDBsReglSQLExc, ZioDBsSQLExc }
import zio.{ durationInt, Schedule, ZIO, ZLayer }

trait ReglamentService {
  def executeCleanup: ZioDBsSQLExc
}

class ReglamentServiceImpl(db: DatabaseService) extends ReglamentService {

  override def executeCleanup: ZioDBsSQLExc =
    db.executeReglamentCleanup

}

object ReglamentService {
  def live: ZLayer[DatabaseService, Nothing, ReglamentService] =
    ZLayer.fromFunction(new ReglamentServiceImpl(_))

  private def executeCleanup: ZioDBsReglSQLExc =
    ZIO.serviceWithZIO[ReglamentService](_.executeCleanup)

  // todo: rewrite it, take meta from db
  def startReglamentCleanup: ZioDBsReglSQLExc = for {
    _ <- ZIO.logInfo("Start reglament cleanup") *>
      ReglamentService
        .executeCleanup
        .repeat(Schedule.spaced(10.minutes))
        .fork
  } yield ()

}
