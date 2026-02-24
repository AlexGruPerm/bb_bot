package service

import model.{ Ask, GetCommonBalance, GetSymbolsBalance, GetViewDeep, GetViewDeepInvalid, HelpFrom }
import zio.stream.ZStream
import zio.{ Queue, ZIO, ZLayer }

import java.sql.SQLException
import javax.sql.DataSource

trait CommunicationService {
  val runConsumer: ZIO[DataSource, Throwable, Nothing]
}

final class CommunicationServiceLive(queue: Queue[Ask], tg: TelegramService, db: DatabaseService)
    extends CommunicationService {

  private def getMaxWalletBalanceId(ask: Ask): ZIO[DataSource, SQLException, Long] =
    ask match {
      case GetCommonBalance | GetSymbolsBalance => db.getMaxWalletBalanceId
      case _                                    => ZIO.succeed(0L)
    }

  private def handler(ask: Ask): ZIO[DataSource, Throwable, Unit] = for {
    _                  <- ZIO.logInfo(s"Handling ask: ${ask.cmd}")
    maxWalletBalanceId <- getMaxWalletBalanceId(ask)
    _                  <- ask match {
      case GetCommonBalance                 =>
        db.getCommonWalletBalance(maxWalletBalanceId)
          .foldZIO(
            err => tg.sendErrorMessage(GetCommonBalance, s"Error receiving balance: ${err.getMessage}"),
            cwb => tg.sendCommonBalance(cwb)
          )
      case GetSymbolsBalance                =>
        db.getSymbolsBalance(maxWalletBalanceId)
          .foldZIO(
            err => tg.sendErrorMessage(GetSymbolsBalance, s"Error receiving balance: ${err.getMessage}"),
            cwb => tg.sendSymbolsBalance(cwb)
          )
      case HelpFrom(user)                   => tg.sendHelp(user)
      case GetViewDeep(interval, deep_bars) =>
        db.getViewDeep(interval, deep_bars)
          .foldZIO(
            err =>
              tg.sendErrorMessage(GetViewDeep(interval, deep_bars), s"Error receiving view deep: ${err.getMessage}"),
            vd_data => tg.sendViewDeep(interval, deep_bars, vd_data)
          )
      case GetViewDeepInvalid(args)         =>
        tg.sendErrorMessage(
          GetViewDeepInvalid(args),
          s"Invalid parameters [$args] Try /getViewDeep 15 10 (where 15 - interval, 10 - deep bars)"
        )
      // ...
      case _  => ZIO.logInfo("[ANY] ASK in QUEUE")
    }
  } yield ()

  override val runConsumer: ZIO[DataSource, Throwable, Nothing] =
    ZIO.logInfo("run CommunicationService Consumer") *>
      ZStream
        .fromQueue(queue)
        .mapZIO(handler)
        .runDrain
        .forever

}

object CommunicationService {
  val live: ZLayer[AskQueueService with TelegramService with DatabaseService, Nothing, CommunicationService] =
    ZLayer {
      for {
        aq <- ZIO.service[AskQueueService]
        tg <- ZIO.service[TelegramService]
        db <- ZIO.service[DatabaseService]
      } yield new CommunicationServiceLive(aq.askQ, tg, db)
    }
}