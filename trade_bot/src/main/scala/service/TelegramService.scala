package service

import bybit_model.{ AdviceToUser, CommonWalletBalance, SymbolsBalance, ViewDeepLine }
import com.bot4s.telegram.api.declarative.{ Commands, JoinRequests, Updates }
import com.bot4s.telegram.cats.TelegramBot
import com.bot4s.telegram.marshalling.CaseConversions
import com.bot4s.telegram.marshalling.fromJson
import com.bot4s.telegram.methods.{ ApproveChatJoinRequest, DeclineChatJoinRequest }
import com.bot4s.telegram.models.{ ChatMemberUpdated, InputFile, MemberStatus, Update, UpdateType, User }
import com.bot4s.telegram.models.UpdateType.Filters.{ InlineUpdates, MessageUpdates }
import conf.{ AppConfig, TelegramConfig }
import io.circe.syntax.EncoderOps
import model.Ask
import sttp.client4.{ asStringAlways, basicRequest, multipart, multipartFile }
import sttp.client4.armeria.zio.ArmeriaZioBackend
import telegram.{ TelegramCommands, TelegramMethods }
import telegram.TelegramMethods.BlockedByUserReason
import zio.Config.Secret
import zio.http.{ Handler, Method, Path, Request, Response, SSLConfig, Server }
import zio.{ Task, ZIO, ZLayer }
import zio.interop.catz._
import zio._
import zio.http.SSLConfig.Data.FromJavaxNetSsl

import java.io.IOException
import java.nio.file.{ Files, Paths }
import javax.sql.DataSource

private object TelegramBackend {
  lazy val instance =
    zio.Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.run(ArmeriaZioBackend.usingDefaultClient()).getOrThrow())
}

abstract class TelegramService(val conf: TelegramConfig)
    extends TelegramBot[Task](conf.token, TelegramBackend.instance) {
  // these methods are in TelegramMethods
  def sendErrorMessage(command: Ask, message: String, user: User): Task[Unit]
  def sendCommonBalance(cwb: CommonWalletBalance): Task[Unit]
  def sendSymbolsBalance(sb: List[SymbolsBalance]): Task[Unit]
  def sendNewAdvice(advice: List[AdviceToUser]): Task[Unit]
  def sendHelp(user: User): Task[Unit]
  def sendViewDeep(interval: String, deep_bars: Int, vd_data: List[ViewDeepLine], user: User): Task[Unit]
  def sendAdminErrorMessage(message: String): Task[Unit]
  def sendLlmFuturesMetricsAdvice(adviceId: Int, advice: String, adviceText: String): Task[Unit]
  def sendLlmAdviceText(adviceId: Int, adviceText: String, user: User): Task[Unit]

  /**
   * Deactivates the user that blocked the bot: sets `ts_db_end` and `reason` in `data.users` and refreshes the
   * in-memory cache, so subsequent broadcasts skip them. Failures are logged and swallowed on purpose - a blocked user
   * must never take the bot down nor fail the surrounding send.
   */
  def deactivateBlockedUser(tgUserId: Long): Task[Unit]
}

class TelegramServiceImpl(
  config: TelegramConfig,
  private val started: Ref.Synchronized[Boolean],
  queue: Queue[Ask],
  db: DatabaseService,
  us: UsersService,
  ds: DataSource
) extends TelegramService(config)
    with Commands[Task]
    with JoinRequests[Task]
    with Updates[Task]
    with TelegramMethods
    with TelegramCommands {

  // for TelegramMethods
  override def usersService: UsersService = us

  // for TelegramCommands
  override def getAskQueue: Queue[Ask]   = queue
  override def getDB: DatabaseService    = db
  override def getDataSource: DataSource = ds

  override def deactivateBlockedUser(tgUserId: Long): Task[Unit] =
    db.deactivateUser(tgUserId, BlockedByUserReason)
      .provideEnvironment(ZEnvironment(ds))
      .tapBoth(
        err => ZIO.logError(s"Failed to deactivate blocked user tgId=$tgUserId: ${err.getMessage}"),
        _ => us.refreshUsers *> ZIO.logInfo(s"User tgId=$tgUserId deactivated (${BlockedByUserReason})")
      )
      .unit

  /**
   * Telegram delivers `my_chat_member` with new status `Kicked` when the user blocks the bot in a private chat. This is
   * a push notification, unlike the `Forbidden: bot was blocked` error which only shows up on the next send attempt.
   * Bot4s' [[com.bot4s.telegram.api.BotBase.receiveUpdate]] has no branch for `myChatMember`, so it is handled here.
   */
  private def onMyChatMemberUpdated(updated: ChatMemberUpdated): Task[Unit] =
    if (updated.newChatMember.status == MemberStatus.Kicked)
      updateChatLog(updated) *> deactivateBlockedUser(updated.chat.id)
    else ZIO.unit

  private def updateChatLog(updated: ChatMemberUpdated): Task[Unit] =
    ZIO.logInfo(
      s"MyChatMember update: chatId=${updated.chat.id} " +
        s"${updated.oldChatMember.status} -> ${updated.newChatMember.status}"
    )

  override def receiveUpdate(update: Update, botUser: Option[User]): Task[Unit] =
    update.myChatMember match {
      case Some(updated) =>
        onMyChatMemberUpdated(updated).catchAll(err =>
          ZIO.logError(s"Failed to handle my_chat_member update: ${err.getMessage}")
        )
      case None          => super.receiveUpdate(update, botUser)
    }

  private val certPathStr: String = config.pubcertpath

  private def certificate: Option[InputFile] = Some(
    InputFile("certificate", Files.readAllBytes(Paths.get(certPathStr)))
  )

  // Telegram sends `my_chat_member` when the bot gets kicked (i.e. blocked) in a private chat. It must be listed in
  // `allowed_updates` explicitly, otherwise the update is never delivered - the default filter only covers the regular
  // update types. bot4s 6.0.0 has no `UpdateType` member for it (the enumeration cannot be extended), so `SetWebhook`
  // cannot express it. The webhook is therefore configured with a raw JSON request and plain string update names.
  private val rawAllowedUpdates: List[String] =
    (MessageUpdates ++ InlineUpdates).toList.map(ut => CaseConversions.snakenize(ut.toString)) :+ "my_chat_member"

  private val sslConfig: SSLConfig =
    SSLConfig.fromJavaxNetSsl(
      data = SSLConfig
        .Data
        .FromJavaxNetSsl(
          keyManagerSource = FromJavaxNetSsl.File(config.p12certpath),
          keyManagerPassword = Some(Secret(config.keyStorePassword)),
          trustManagerKeyStore = None
        ),
      includeClientCert = false,
      clientAuth = None
    )

  private val serverConfig =
    ZLayer.succeed {
      Server
        .Config
        .default
        .port(config.webhook_port)
        .ssl(sslConfig)
    }

  private def server: ZIO[Any, Throwable, Nothing] =
    Server.serve(callback.toRoutes).provide(serverConfig, Server.live)

  private def callback: Handler[Any, Nothing, Request, Response] =
    Handler.fromFunctionZIO[Request] {
      case req if req.method == Method.POST && req.path == Path.root =>
        for {
          body   <- req.body.asString.orDie
          update <- ZIO.attempt(fromJson[Update](body)).orDie
          _      <- receiveUpdate(update, None).catchAll { ex: Throwable =>
            ZIO.logError(s"receiveUpdate exception ${ex.getMessage} - ${ex.getCause}")
          }
        } yield Response.ok
      case _                                                         => ZIO.succeed(Response.notFound)
    }

  /**
   * Sends a multipart `setWebhook` request whose `allowed_updates` is a plain JSON array of strings, so
   * `my_chat_member` can be requested even though bot4s models `allowedUpdates` as `Seq[UpdateType]`.
   */
  private def setWebhookRequest(certificateFile: InputFile): Task[Boolean] = {
    val fields: List[(String, String)] = List(
      "url"             -> config.webhookUrl,
      "allowed_updates" -> rawAllowedUpdates.asJson.noSpaces
    )
    val certificatePart                = certificateFile match {
      case InputFile.Contents(filename, contents) => multipart("certificate", contents).fileName(filename)
      case InputFile.Path(path)                   => multipartFile("certificate", path)
      case other                                  => throw new IllegalArgumentException(s"Unsupported certificate $other")
    }
    val parts                          = fields.map { case (name, value) => multipart(name, value) } :+ certificatePart
    val url                            = sttp.model.Uri.unsafeParse(s"https://api.telegram.org/bot${config.token}/setWebhook")
    val req                            = basicRequest
      .post(url)
      .multipartBody(parts)
      .response(asStringAlways)
    req
      .send(TelegramBackend.instance)
      .flatMap { resp =>
        val body = resp.body
        if (resp.isSuccess && body.contains("\"ok\":true")) ZIO.logInfo("SetWebhook success.").as(true)
        else ZIO.logError(s"SetWebhook failed: $body").as(false)
      }
      .catchAll(err => ZIO.logError(s"SetWebhook exception ${err.getMessage}").as(false))
  }

  private def startBot: ZIO[Any, Throwable, Unit] = started.updateZIO { isStarted =>
    for {
      _        <- ZIO.when(isStarted)(ZIO.fail(new Exception("Bot already started")))
      _        <- ZIO.when(!isStarted)(ZIO.logInfo(s"Bot not started yet, starting it .... webhookUrl=${config.webhookUrl}"))
      response <- setWebhookRequest(certificate.get)
    } yield response
  }

  override def run(): Task[Unit] =
    for {
      _ <- ZIO.logInfo(s"Start telegram bot. run()")
      _ <- server.forkDaemon
      _ <- startBot.forkDaemon
    } yield ()

  private val accept = true

  onJoinRequest { joinRequest =>
    onJoinCommandLog(joinRequest.chat.id, joinRequest.from.id) *>
      (if (accept) {
         request(ApproveChatJoinRequest(joinRequest.chat.chatId, joinRequest.from.id)).ignore
       } else {
         request(DeclineChatJoinRequest(joinRequest.chat.chatId, joinRequest.from.id)).ignore
       })
  }

  private def onJoinCommandLog(chatId: Long, userId: Long): ZIO[Any, IOException, Unit] =
    for {
      console <- ZIO.console
      _       <- console.printLine(s" Join Command From [$userId] to chat [$chatId] ~~~~~~~~~~~ ")
    } yield ()

}

object TelegramService {
  val live: ZLayer[
    AppConfig with AskQueueService with DatabaseService with DataSource with UsersService,
    Throwable,
    TelegramService
  ] =
    ZLayer.scoped {
      for {
        config  <- ZIO.service[AppConfig].map(_.telegram)
        started <- Ref.Synchronized.make(false)
        queue   <- ZIO.service[AskQueueService]
        db      <- ZIO.service[DatabaseService]
        us      <- ZIO.service[UsersService]
        ds      <- ZIO.service[DataSource]
        service  = new TelegramServiceImpl(config, started, queue.askQ, db, us, ds)
      } yield service
    }
}
