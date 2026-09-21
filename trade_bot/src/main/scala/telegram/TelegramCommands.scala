package telegram

import bybit_model.{ DbUser, UsersInsert }
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.methods.{ ParseMode, SendMessage }
import com.bot4s.telegram.models.{ Message, User }
import model.{ Ask, GetCommonBalance, GetSymbolsBalance, GetViewDeep, GetViewDeepInvalid, HelpFrom }
import service.{ DatabaseService, UsersService }
import zio.{ Queue, Task, UIO, ZEnvironment, ZIO }

import java.io.IOException
import java.sql.Timestamp
import javax.sql.DataSource

trait TelegramCommands {
  self: Commands[Task] =>

  def getAskQueue: Queue[Ask]
  def getDB: DatabaseService
  def usersService: UsersService
  def getDataSource: DataSource

  private val AccessDeniedMsg = "Доступ к боту запрещён. Обратитесь к администратору."
  private val AdminOnlyMsg    = "Данная команда доступна только администратору."

  private def putToQueue(ask: Ask): UIO[Boolean] =
    getAskQueue.offer(ask)

  private def sendToUser(tgUserId: Long, text: String): Task[Unit] =
    request(SendMessage(tgUserId, text, Some(ParseMode.HTML))).ignore.unit

  private def logCommand(idUser: Int, cmd: String): Task[Unit] =
    getDB
      .saveTgCommandLog(idUser, cmd)
      .provideEnvironment(ZEnvironment(getDataSource))
      .tapError(e => ZIO.logError(s"Failed to log command [$cmd] for user id=$idUser: ${e.getMessage}"))
      .ignore

  private def toUsersInsert(tgUser: User): UsersInsert =
    UsersInsert(
      user_id = tgUser.id,
      is_bot = tgUser.isBot,
      first_name = tgUser.firstName,
      last_name = tgUser.lastName,
      username = tgUser.username,
      language_code = tgUser.languageCode,
      is_premium = tgUser.isPremium,
      added_to_attachment_menu = tgUser.addedToAttachmentMenu,
      can_join_groups = tgUser.canJoinGroups,
      can_read_all_group_messages = tgUser.canReadAllGroupMessages,
      supports_inline_queries = tgUser.supportsInlineQueries
    )

  private def ensureUser(tgUser: User): UIO[Option[DbUser]] =
    usersService.findUser(tgUser.id).flatMap {
      case Some(u) => ZIO.succeed(Some(u))
      case None    =>
        getDB
          .upsertUser(toUsersInsert(tgUser))
          .provideEnvironment(ZEnvironment(getDataSource))
          .tapError(e => ZIO.logError(s"Failed to upsert tg user ${tgUser.id}: ${e.getMessage}"))
          .foldZIO(
            _ => usersService.findUser(tgUser.id),
            _ => usersService.refreshUsers *> usersService.findUser(tgUser.id)
          )
    }

  private def processCommand[A](cmd: String, requiresAdmin: Boolean)(
    action: Task[A]
  )(implicit msg: Message): Task[Unit] =
    msg.from match {
      case None         =>
        ZIO.logError(s"Command [$cmd] received without 'from' user")
      case Some(tgUser) =>
        for {
          userOpt <- ensureUser(tgUser)
          _       <- userOpt match {
            case Some(u) => logCommand(u.id, cmd)
            case None    => ZIO.logInfo(s"User tgId=${tgUser.id} not found in data.users, command [$cmd] not logged")
          }
          _       <- userOpt match {
            case None                                                              => sendToUser(tgUser.id, AccessDeniedMsg)
            case Some(u) if !u.isActive(new Timestamp(System.currentTimeMillis())) =>
              sendToUser(tgUser.id, AccessDeniedMsg)
            case Some(u) if requiresAdmin && !u.is_admin                           => sendToUser(tgUser.id, AdminOnlyMsg)
            case Some(_)                                                           => action.unit
          }
        } yield ()
    }

  onCommand("/getCommonBalance") { implicit msg =>
    processCommand(cmd = "/getCommonBalance", requiresAdmin = true) {
      putToQueue(GetCommonBalance)
    }
  }

  onCommand("/getSymbolsBalance") { implicit msg =>
    processCommand(cmd = "/getSymbolsBalance", requiresAdmin = true) {
      putToQueue(GetSymbolsBalance)
    }
  }

  onCommand("/getBalance") { implicit msg =>
    processCommand(cmd = "/getBalance", requiresAdmin = true) {
      putToQueue(GetCommonBalance) *> putToQueue(GetSymbolsBalance)
    }
  }

  onCommand("/help") { implicit msg =>
    onCommandLog(msg) *>
      processCommand(cmd = "/help", requiresAdmin = false) {
        msg.from match {
          case Some(u) => putToQueue(HelpFrom(u))
          case None    => ZIO.unit
        }
      }
  }

  onCommand("/getViewDeep_15_10") { implicit msg =>
    processCommand(cmd = "/getViewDeep_15_10", requiresAdmin = false) {
      putToQueue(GetViewDeep("15", 10))
    }
  }

  onCommand("/getViewDeep" | "/getviewdeep" | "/gvd") { implicit msg =>
    withArgs {
      case Seq(param1, param2) =>
        val interval: String = param1
        val deepBars: Int    = param2.toInt
        processCommand(cmd = s"/getViewDeep $param1 $param2", requiresAdmin = false) {
          putToQueue(GetViewDeep(interval, deepBars))
        }
      case args                =>
        processCommand(cmd = "/getViewDeep", requiresAdmin = false) {
          putToQueue(GetViewDeepInvalid(args.mkString))
        }
    }
  }

  onCommand("/start") { implicit msg =>
    processCommand(cmd = "/start", requiresAdmin = false) {
      for {
        _ <- onCommandLog(msg)
        _ <- msg.from.fold(ZIO.logInfo("User is empty")) { u: User =>
          ZIO.logInfo(s" lastName=${u.lastName.getOrElse(" ")} username= ${u.username.getOrElse(" ")}")
        }
        r <- reply("start command!").ignore
      } yield r
    }
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
