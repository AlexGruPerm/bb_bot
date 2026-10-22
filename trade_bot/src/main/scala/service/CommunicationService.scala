package service

import model.{
  Ask,
  GetCommonBalance,
  GetLlmAdviceDescr,
  GetLlmAdviceDescrInvalid,
  GetSymbolsBalance,
  GetViewDeep,
  GetViewDeepInvalid,
  HelpFrom,
  LlmFuturesMetricsAdvice,
  SendAdminErrorLog
}
import zio.stream.ZStream
import zio.{ Queue, Task, ZIO, ZLayer }

import java.sql.SQLException
import javax.sql.DataSource

trait CommunicationService {
  val runConsumer: ZIO[DataSource, Throwable, Nothing]
}

final class CommunicationServiceLive(queue: Queue[Ask], tg: TelegramService, db: DatabaseService)
    extends CommunicationService {

  /**
   * The latest wallet balance id is needed only by the balance commands, so it is fetched inside those branches instead
   * of unconditionally for every ask. Failures of the id query are reported to the user via `onError`.
   */
  private def withMaxWalletBalanceId(
    onError: Throwable => Task[Unit]
  )(use: Long => ZIO[DataSource, Throwable, Unit]): ZIO[DataSource, Throwable, Unit] =
    db.getMaxWalletBalanceId.foldZIO(onError, use)

  private def handler(ask: Ask): ZIO[DataSource, Throwable, Unit] = for {
    _ <- ZIO.logDebug(s"Handling ask: ${ask.cmd}")
    _ <- ask match {
      case GetCommonBalance(user)                                                      =>
        withMaxWalletBalanceId(err =>
          tg.sendErrorMessage(GetCommonBalance(user), s"Error receiving balance: ${err.getMessage}", user)
        ) { id =>
          db.getCommonWalletBalance(id)
            .tapBoth(
              err => tg.sendErrorMessage(GetCommonBalance(user), s"Error receiving balance: ${err.getMessage}", user),
              tg.sendCommonBalance
            )
            .unit
        }
      case GetSymbolsBalance(user)                                                     =>
        withMaxWalletBalanceId(err =>
          tg.sendErrorMessage(GetSymbolsBalance(user), s"Error receiving balance: ${err.getMessage}", user)
        ) { id =>
          db.getSymbolsBalance(id)
            .tapBoth(
              err => tg.sendErrorMessage(GetSymbolsBalance(user), s"Error receiving balance: ${err.getMessage}", user),
              tg.sendSymbolsBalance
            )
            .unit
        }
      case HelpFrom(user)                                                              => tg.sendHelp(user)
      case GetViewDeep(interval, deep_bars, user)                                      =>
        db.getViewDeep(interval, deep_bars)
          .foldZIO(
            err =>
              tg.sendErrorMessage(
                GetViewDeep(interval, deep_bars, user),
                s"Error receiving view deep: ${err.getMessage}",
                user
              ),
            vd_data => tg.sendViewDeep(interval, deep_bars, vd_data, user)
          )
      case GetViewDeepInvalid(args, user)                                              =>
        tg.sendErrorMessage(
          GetViewDeepInvalid(args, user),
          s"Invalid parameters [$args] Try /getViewDeep 15 10 (where 15 - interval, 10 - deep bars)",
          user
        )
      case SendAdminErrorLog(message)                                                  => tg.sendAdminErrorMessage(message)
      case LlmFuturesMetricsAdvice(promptId, compIntMin, everyMin, advice, adviceText) =>
        tg.sendLlmFuturesMetricsAdvice(promptId, compIntMin, everyMin, advice, adviceText)
      case GetLlmAdviceDescr(adviceId, user)                                           =>
        db.getLlmAdviceText(adviceId)
          .foldZIO(
            err =>
              tg.sendErrorMessage(
                GetLlmAdviceDescr(adviceId, user),
                s"Error receiving advice text: ${err.getMessage}",
                user
              ),
            {
              case Some(text) => tg.sendLlmAdviceText(adviceId, text, user)
              case None       => tg.sendErrorMessage(GetLlmAdviceDescr(adviceId, user), s"Advice #$adviceId not found", user)
            }
          )
      case GetLlmAdviceDescrInvalid(args, user)                                        =>
        tg.sendErrorMessage(
          GetLlmAdviceDescrInvalid(args, user),
          s"Invalid parameters [$args] Try /get_hold_descr_X (where X - advice id)",
          user
        )
      // ...
      case _                                                                           => ZIO.logInfo("[ANY] ASK in QUEUE")
    }
  } yield ()

  // A single failed ask (e.g. Telegram rejecting a message) must not take the whole bot down,
  // so handler errors are logged and swallowed per-element.
  private def safeHandler(ask: Ask): ZIO[DataSource, Nothing, Unit] =
    handler(ask).tapError(err => ZIO.logError(s"Ask [${ask.cmd}] failed: ${err.getMessage}")).ignore

  override val runConsumer: ZIO[DataSource, Throwable, Nothing] =
    ZIO.logInfo("run CommunicationService Consumer") *>
      ZStream
        .fromQueue(queue)
        .mapZIO(safeHandler)
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
