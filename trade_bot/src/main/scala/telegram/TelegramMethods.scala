package telegram

import app.UserId
import bybit_model.{AdviceToUser, AdviceToUserKey, CommonWalletBalance, SymbolsBalance, ViewDeepLine }
import com.bot4s.telegram.cats.TelegramBot
import com.bot4s.telegram.methods.{ ParseMode, SendMessage }
import com.bot4s.telegram.models.User
import model.Ask
import zio.{ Task, UIO, ZIO }

import java.time.format.DateTimeFormatter
import scala.math.BigDecimal.double2bigDecimal

trait TelegramMethods {
  self: TelegramBot[Task] =>

  def usersId: List[UserId]

  private val double2str: Double => String = d => d.setScale(2, BigDecimal.RoundingMode.HALF_UP).toString()

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
                   |${sb.map { s =>
                    s"${s.symbol_id.getOrElse("-").toString.padTo(2, ' ')} " +
                      s" ${s.symbol_code.getOrElse("-").padTo(8, ' ')} " +
                      s"${s.coin_code.padTo(5, ' ')} " +
                      s"${double2str(s.equity).padTo(8, ' ')} " +
                      s"${double2str(s.usdvalue)}"
                  }.mkString("\n")}
                   |</pre>
                   |""".stripMargin)

  private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
  private def formatSavedAdvice(advice: Map[AdviceToUserKey, List[AdviceToUser]]): UIO[String] =
    ZIO.succeed(
      advice.map { case (k, v) =>
        s""" <b>New advice (interval ${k.c_interval} mins.)</b>
           |
           | Advisor (<b>${k.adviser_id}</b> - ${k.proc})
           |  <i>${k.advice_description}</i>
           |
           |<pre>#   Candle                  Symbol       Time                  last Price     Advice
           |${v.map { a =>
            s"${a.advice_id}  ${a.id_candle}(${a.start_ts})   ${a.symbol_code}(${a.id_symbol})  " +
              s" ${formatter.format(a.ts_db.toLocalDateTime.withNano(0))}   ${a.last_price}       " +
              s"   ${a.advice} "
          }.mkString("\n")}
           |</pre>
           |/getBalance
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
         |${vd_data.map { v =>
          s"${v.code.getOrElse("-").padTo(8, ' ')} " +
            s"${doubleOpt2str(v.p_first).padTo(9, ' ')} " +
            s"${doubleOpt2str(v.c_last).padTo(9, ' ')} " +
            s"${doubleOpt2str(v.dif_prcnt).padTo(12, ' ')} " +
            s"${v.move_dir.padTo(9, ' ')}  " +
            s"${doubleOpt2str(v.smpl_volat).padTo(8, ' ')} "
        }.mkString("\n")}
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
                   |/getBalance          - Common balance
                   |/getCommonBalance    - Only total balance
                   |/getSymbolsBalance   - Balance by symbols
                   |/getViewDeep X Y     - where X - interval in minutes, Y - deep bars
                   |/getViewDeep_15_10   - Fixed parameters: 15 minutes, 10 bars
                   |
                   |/help - help page
                   |
                   |""".stripMargin)

  private def sendToAllUsers(msgFormatter: UIO[String]): Task[Unit] =
    ZIO
      .foreach(usersId) { userId =>
        msgFormatter.flatMap { msg =>
          request(SendMessage(userId, msg, Some(ParseMode.HTML)))
        }
      }
      .tapError { e: Throwable =>
        ZIO.logError(s"${e.getMessage} - ${e.getMessage}")
      }
      .unit

  def sendCommonBalance(cwb: CommonWalletBalance): Task[Unit] =
    sendToAllUsers(formatCommonBalance(cwb))

  def sendSymbolsBalance(sb: List[SymbolsBalance]): Task[Unit] =
    sendToAllUsers(formatSymbolsBalance(sb))

  def sendNewAdvice(advice: List[AdviceToUser]): Task[Unit] =
    sendToAllUsers(
      formatSavedAdvice(
        advice.groupBy(a => AdviceToUserKey(a.adviser_id, a.advice_description, a.proc, a.c_interval))
      )
    )

  def sendHelp(user: User): Task[Unit] =
    sendToAllUsers(formatHelp(user))

  def sendViewDeep(interval: String, deep_bars: Int, vd_data: List[ViewDeepLine]): Task[Unit] =
    sendToAllUsers(
      formatViewDeep(interval, deep_bars, vd_data)
    )

  def sendErrorMessage(command: Ask, message: String): Task[Unit] =
    sendToAllUsers(ZIO.succeed(s"""<b>${command.cmd}</b>
                                  |$message
                                  |""".stripMargin))

}