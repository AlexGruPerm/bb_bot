package service

import app.{ ZioDBsReglSQLExc, ZioDBsSQLExc }
import zio.{ durationInt, Schedule, ZIO, ZLayer }

trait ReglamentService {
  def executeCleanup(code: String): ZioDBsSQLExc
}

class ReglamentServiceImpl(db: DatabaseService) extends ReglamentService {

  override def executeCleanup(code: String): ZioDBsSQLExc =
    db.executeReglamentCleanup(code)

}

object ReglamentService {
  def live: ZLayer[DatabaseService, Nothing, ReglamentService] =
    ZLayer.fromFunction(new ReglamentServiceImpl(_))

  private def executeWalletBalanceCleanup: ZioDBsReglSQLExc =
    ZIO.serviceWithZIO[ReglamentService](_.executeCleanup("keep_wb_days"))

  private def executeOrderBookCleanup: ZioDBsReglSQLExc =
    ZIO.serviceWithZIO[ReglamentService](_.executeCleanup("keep_obs_days"))

  private def executeCandleCleanup: ZioDBsReglSQLExc =
    ZIO.serviceWithZIO[ReglamentService](_.executeCleanup("keep_candle_days"))

  def startReglamentCleanup: ZioDBsReglSQLExc = for {

    _ <- ReglamentService
      .executeWalletBalanceCleanup
      .repeat(Schedule.spaced(10.minutes))
      .delay(0.seconds)
      .fork

    _ <- ReglamentService
      .executeOrderBookCleanup
      .repeat(Schedule.spaced(10.minutes))
      .delay(3.minutes)
      .fork

    _ <- ReglamentService
      .executeCandleCleanup
      .repeat(Schedule.spaced(10.minutes))
      .delay(6.minutes)
      .fork

  } yield ()

}
