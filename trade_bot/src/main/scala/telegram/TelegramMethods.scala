package telegram

import bybit_model.{ AdviceToUser, AdviceToUserKey, CommonWalletBalance, SymbolsBalance, ViewDeepLine }
import com.bot4s.telegram.cats.TelegramBot
import com.bot4s.telegram.methods.{ ParseMode, SendMessage }
import com.bot4s.telegram.models.User
import model.Ask
import service.UsersService
import zio.{ Task, UIO, ZIO }

import java.time.format.DateTimeFormatter
import scala.math.BigDecimal.double2bigDecimal

object TelegramMethods {

  /** Stored in data.users.reason when Telegram reports that the user blocked the bot. */
  val BlockedByUserReason: String = "bot was blocked by the user"
}

trait TelegramMethods {
  self: TelegramBot[Task] =>

  import TelegramMethods.BlockedByUserReason

  def usersService: UsersService

  /** Implemented by [[service.TelegramService]]: marks the user inactive in `data.users` and refreshes the cache. */
  def deactivateBlockedUser(tgUserId: Long): Task[Unit]

  /**
   * Telegram returns "Forbidden: bot was blocked by the user" when the recipient deleted/blocked the bot. The message
   * does not carry the chat id, so the id comes from the failed request in [[sendToTelegramUsers]].
   */
  private def isBlockedByUser(err: Throwable): Boolean =
    Option(err.getMessage).exists(_.toLowerCase.contains(BlockedByUserReason))

  private val double2str: Double => String = d => d.setScale(2, BigDecimal.RoundingMode.HALF_UP).toString()

  /**
   * Escapes '<', '>' and '&' in arbitrary text before embedding it into a ParseMode.HTML message.
   *
   * LLM-generated advice text may contain things like "<2% ОИ" (a bare '<' followed by a digit) or "a < b", which
   * Telegram's HTML parser rejects with "Unsupported start tag", aborting the whole bot.
   */
  private def escapeHtml(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")

  private val doubleOpt2str: Option[Double] => String = {
    case Some(d) =>
      if (d > 0)
        " " + double2str(d)
      else
        double2str(d)
    case None    => " "
  }

  private def formatCommonBalance(cwb: CommonWalletBalance): UIO[String] =
    if (cwb.is_actual)
      ZIO.succeed(
        s"""Current common balance:
           |<b>${double2str(cwb.totalequity)}</b> USD.""".stripMargin
      )
    else
      ZIO.succeed(
        s"""<b>(Time lag)</b> Last balance:
           |<i>${cwb.totalequity}</i> USD.
           | Time lag           = ${cwb.diff_seconds} sec.
           | Latest data time   : ${cwb.ts_bybit}
           | Current date time  : ${cwb.ts_current}
           |""".stripMargin
      )

  private def formatSymbolsBalance(sb: List[SymbolsBalance]): UIO[String] =
    ZIO.succeed(s"""Current balance by symbols:
                   |<pre>id  code     coin  equity   usdvalue
                   |${escapeHtml(sb.map { s =>
                    s"${s.symbol_id.getOrElse("-").toString.padTo(2, ' ')} " +
                      s" ${s.symbol_code.getOrElse("-").padTo(8, ' ')} " +
                      s"${s.coin_code.padTo(5, ' ')} " +
                      s"${double2str(s.equity).padTo(8, ' ')} " +
                      s"${double2str(s.usdvalue)}"
                  }.mkString("\n"))}
                   |</pre>
                   |""".stripMargin)

  private val formatter                                                                        = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
  private def formatSavedAdvice(advice: Map[AdviceToUserKey, List[AdviceToUser]]): UIO[String] =
    ZIO.succeed(
      advice.map { case (k, v) =>
        s""" <b>New advice (interval ${k.c_interval} mins.)</b>
           |
           | Advisor (<b>${k.adviser_id}</b> - ${k.proc})
           |  <i>${k.advice_description}</i>
           |
           |<pre>#   Candle                  Symbol       Time                  last Price     Advice
           |${escapeHtml(v.map { a =>
            s"${a.advice_id}  ${a.id_candle}(${a.start_ts})   ${a.symbol_code}(${a.id_symbol})  " +
              s" ${formatter.format(a.ts_db.toLocalDateTime.withNano(0))}   ${a.last_price}       " +
              s"   ${a.advice} "
          }.mkString("\n"))}
           |</pre>
           |/help
           |""".stripMargin
      }.mkString("\n")
    )

  private def formatViewDeep(interval: String, deep_bars: Int, vd_data: List[ViewDeepLine]): UIO[String] =
    ZIO.succeed(
      s"""
         |<b>Deep dive into the market</b>
         | ($interval minutes) $deep_bars bars
         |
         |<pre>code      p_first   c_last    dif_prcnt   move_dir    smpl_volat
         |${escapeHtml(vd_data.map { v =>
          s"${v.code.getOrElse("-").padTo(8, ' ')} " +
            s"${doubleOpt2str(v.p_first).padTo(9, ' ')} " +
            s"${doubleOpt2str(v.c_last).padTo(9, ' ')} " +
            s"${doubleOpt2str(v.dif_prcnt).padTo(12, ' ')} " +
            s"${v.move_dir.padTo(9, ' ')}  " +
            s"${doubleOpt2str(v.smpl_volat).padTo(8, ' ')} "
        }.mkString("\n"))}
         |</pre>
         | /help
         |""".stripMargin
    )

  private def formatHelp(user: User): UIO[String] =
    ZIO.succeed(s"""<b>User</b>
                   |@${user.username.getOrElse("")}  ( ${user.firstName} ${user.lastName.getOrElse("")} )
                   |
                   |Main bot commands:
                   |
                   |/getViewDeep X Y     - where X - interval in minutes, Y - deep bars
                   |/gvd X Y             - alias for getViewDeep
                   |/getViewDeep_15_10   - Fixed parameters: 15 minutes, 10 bars
                   |/get_hold_descr_X    - explanation of a "hold" advice, where X - advice id
                   |
                   |/help - help page
                   |
                   |""".stripMargin)

  private def formatHelpAdmin(user: User): UIO[String] =
    ZIO.succeed(s"""<b>User</b>
                   |@${user.username.getOrElse("")}  ( ${user.firstName} ${user.lastName.getOrElse("")} )
                   |
                   |Main bot commands:
                   |
                   |/getBalance          - Common balance
                   |/getCommonBalance    - Only total balance
                   |/getSymbolsBalance   - Balance by symbols
                   |/getViewDeep X Y     - where X - interval in minutes, Y - deep bars
                   |/gvd X Y             - alias for getViewDeep
                   |/getViewDeep_15_10   - Fixed parameters: 15 minutes, 10 bars
                   |/get_hold_descr_X    - explanation of a "hold" advice, where X - advice id
                   |
                   |/help - help page
                   |
                   |""".stripMargin)

  private def sendToTelegramUsers(userIds: List[Long], msg: String): Task[Unit] =
    ZIO
      .foreach(userIds) { userId =>
        request(SendMessage(userId, msg, Some(ParseMode.HTML))).catchAll { e: Throwable =>
          if (isBlockedByUser(e)) {
            // The user blocked the bot: remember it in data.users and keep sending to the rest.
            ZIO.logWarning(s"User tgId=$userId blocked the bot, deactivating and skipping") *>
              deactivateBlockedUser(userId).ignore
          } else ZIO.fail(e)
        }
      }
      .tapError { e: Throwable =>
        ZIO.logError(s"Failed to send telegram message: ${e.getMessage}")
      }
      .unit

  // For sending to everyone (advices)
  private def sendToActiveUsers(msg: String): Task[Unit] =
    usersService.activeUsers.flatMap(us => sendToTelegramUsers(us.map(_.user_id), msg))

  private def sendToActiveAdmins(msg: String): Task[Unit] =
    usersService.activeAdmins.flatMap(us => sendToTelegramUsers(us.map(_.user_id), msg))

  private def sendToUser(userId: Long, msg: String): Task[Unit] =
    sendToTelegramUsers(List(userId), msg)

  def sendCommonBalance(cwb: CommonWalletBalance): Task[Unit] =
    formatCommonBalance(cwb).flatMap(sendToActiveAdmins)

  def sendSymbolsBalance(sb: List[SymbolsBalance]): Task[Unit] =
    formatSymbolsBalance(sb).flatMap(sendToActiveAdmins)

  def sendNewAdvice(advice: List[AdviceToUser]): Task[Unit] =
    formatSavedAdvice(
      advice.groupBy(a => AdviceToUserKey(a.adviser_id, a.advice_description, a.proc, a.c_interval))
    ).flatMap(sendToActiveUsers)

  def sendHelp(user: User): Task[Unit] =
    for {
      isAdmin <- usersService.findUser(user.id).map(_.exists(_.is_admin))
      msg     <- if (isAdmin) formatHelpAdmin(user) else formatHelp(user)
      _       <- sendToUser(user.id, msg)
    } yield ()

  def sendViewDeep(interval: String, deep_bars: Int, vd_data: List[ViewDeepLine], user: User): Task[Unit] =
    formatViewDeep(interval, deep_bars, vd_data).flatMap(sendToUser(user.id, _))

  def sendErrorMessage(command: Ask, message: String, user: User): Task[Unit] =
    sendToUser(
      user.id,
      s"""<b>${escapeHtml(command.cmd)}</b>
         |${escapeHtml(message)}
         |""".stripMargin
    )

  def sendAdminErrorMessage(message: String): Task[Unit] =
    sendToActiveAdmins(
      s"""<b>Admin error alert</b>
         |${escapeHtml(message)}
         |""".stripMargin
    )

  def sendLlmFuturesMetricsAdvice(adviceId: Int, advice: String, adviceText: String): Task[Unit] =
    if (advice.equalsIgnoreCase("hold"))
      sendToActiveUsers(
        s"""<b>hold</b>
           |
           |/get_hold_descr_$adviceId""".stripMargin
      )
    else
      sendToActiveUsers(
        s"""<b>${escapeHtml(advice)}</b>
           |
           |${escapeHtml(adviceText)}""".stripMargin
      )

  def sendLlmAdviceText(adviceId: Int, adviceText: String, user: User): Task[Unit] =
    sendToUser(
      user.id,
      s"""<b>advice #$adviceId</b>
         |
         |${escapeHtml(adviceText)}""".stripMargin
    )

}
