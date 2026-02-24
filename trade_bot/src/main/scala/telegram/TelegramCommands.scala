package telegram

import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.models.{ Message, User }
import model.{ Ask, GetCommonBalance, GetSymbolsBalance, GetViewDeep, GetViewDeepInvalid, HelpFrom }
import service.DatabaseService
import zio.{ Queue, Task, UIO, ZIO }

import java.io.IOException

trait TelegramCommands {
  self: Commands[Task] =>

  def getAskQueue: Queue[Ask]
  def getDB: DatabaseService

  private def putToQueue(ask: Ask): UIO[Boolean] =
    getAskQueue.offer(ask)

  onCommand("/getCommonBalance") { implicit msg =>
    putToQueue(GetCommonBalance) *> ZIO.unit
  }

  onCommand("/getSymbolsBalance") { implicit msg =>
    putToQueue(GetSymbolsBalance) *> ZIO.unit
  }

  onCommand("/getBalance") { implicit msg =>
    putToQueue(GetCommonBalance) *>
      putToQueue(GetSymbolsBalance) *> ZIO.unit
  }

  onCommand("/help") { implicit msg =>
    using(_.from) { user =>
      putToQueue(HelpFrom(user)) *> ZIO.unit
    }
  }

  onCommand("/getViewDeep_15_10") { implicit msg =>
    putToQueue(GetViewDeep("15", 10)) *> ZIO.unit
  }

  onCommand("/getViewDeep") { implicit msg =>
    withArgs {
      case Seq(param1, param2) =>
        val interval: String = param1
        val deepBars: Int    = param2.toInt
        putToQueue(GetViewDeep(interval, deepBars)) *> ZIO.unit
      case args                =>
        putToQueue(GetViewDeepInvalid(args.mkString)) *> ZIO.unit
    }
  }

  onCommand("/start") { implicit msg =>
    for {
      _ <- onCommandLog(msg)
      _ <- msg.from.fold(ZIO.logInfo("User is empty")) { u: User =>
        ZIO.logInfo(s" lastName=${u.lastName.getOrElse(" ")} username= ${u.username.getOrElse(" ")}")
      }
      r <- reply("start command!").ignore
    } yield r
  }

  private def onCommandLog(msg: Message): ZIO[Any, IOException, Unit] =
    for {
      _          <- ZIO.logInfo(" Command ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ ")
      userId      = msg.from.map(u => u.id).getOrElse(" ")
      chatId      = msg.chat.id
      userSurname = msg.from.map(u => u.lastName.getOrElse(" ")).getOrElse(" ")
      userLogin   = msg.from.map(u => u.username.getOrElse(" ")).getOrElse(" ")
      msgId       = msg.messageId
      info        = s"msg[$msgId] User[$userId]: $userSurname - $userLogin chat_id = $chatId"
      _          <- ZIO.logInfo(info)
      _          <- ZIO.logInfo(" ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ ")
    } yield ()

}
