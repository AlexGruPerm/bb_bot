package service

import app.UserId
import bybit_model.{ AdviceToUser, CommonWalletBalance, SymbolsBalance, ViewDeepLine }
import com.bot4s.telegram.api.declarative.{ Commands, JoinRequests }
import com.bot4s.telegram.cats.TelegramBot
import com.bot4s.telegram.marshalling.fromJson
import com.bot4s.telegram.methods.{ ApproveChatJoinRequest, DeclineChatJoinRequest, SetWebhook }
import com.bot4s.telegram.models.{ InputFile, Update, UpdateType, User }
import com.bot4s.telegram.models.UpdateType.Filters.{ InlineUpdates, MessageUpdates }
import conf.{ AppConfig, TelegramConfig }
import model.Ask
import org.asynchttpclient.Dsl.asyncHttpClient
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import telegram.{ TelegramCommands, TelegramMethods }
import zio.Config.Secret
import zio.http.{ Handler, Method, Path, Request, Response, SSLConfig, Server }
import zio.{ Task, ZIO, ZLayer }
import zio.interop.catz._
import zio._
import zio.http.SSLConfig.Data.FromJavaxNetSsl

import java.io.IOException
import java.nio.file.{ Files, Paths }

abstract class TelegramService(val conf: TelegramConfig)
    extends TelegramBot[Task](
      conf.token,
      AsyncHttpClientZioBackend.usingClient(zio.Runtime.default, asyncHttpClient())
    ) {
  // these methods are in TelegramMethods
  def sendErrorMessage(command: Ask, message: String): Task[Unit]
  def sendCommonBalance(cwb: CommonWalletBalance): Task[Unit]
  def sendSymbolsBalance(sb: List[SymbolsBalance]): Task[Unit]
  def sendNewAdvice(advice: List[AdviceToUser]): Task[Unit]
  def sendHelp(user: User): Task[Unit]
  def sendViewDeep(interval: String, deep_bars: Int, vd_data: List[ViewDeepLine]): Task[Unit]
}

class TelegramServiceImpl(
  config: TelegramConfig,
  private val started: Ref.Synchronized[Boolean],
  queue: Queue[Ask],
  db: DatabaseService
) extends TelegramService(config)
    with Commands[Task]
    with JoinRequests[Task]
    with TelegramMethods
    with TelegramCommands {

  // for TelegramMethods
  override def usersId: List[UserId] = config.users

  // queue and DbService for TelegramCommands
  override def getAskQueue: Queue[Ask] = queue
  override def getDB: DatabaseService  = db

  private val certPathStr: String = config.pubcertpath

  private def certificate: Option[InputFile] = Some(
    InputFile("certificate", Files.readAllBytes(Paths.get(certPathStr)))
  )

  override def allowedUpdates: Some[Seq[UpdateType.UpdateType]] =
    Some(MessageUpdates ++ InlineUpdates)

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

  private def startBot: ZIO[Any, Throwable, Unit] = started.updateZIO { isStarted =>
    for {
      _        <- ZIO.when(isStarted)(ZIO.fail(new Exception("Bot already started")))
      _        <- ZIO.when(!isStarted)(ZIO.logInfo(s"Bot not started yet, starting it .... webhookUrl=${config.webhookUrl}"))
      response <- request(
        SetWebhook(url = config.webhookUrl, certificate = certificate, allowedUpdates = allowedUpdates)
      ).flatMap {
        case true  => ZIO.logInfo("SetWebhook success.").as(true)
        case false => ZIO.fail(throw new RuntimeException("Failed to set webhook"))
      }.catchAllDefect(ex =>
        ZIO.logError(s"SetWebhook exception ${ex.getMessage} - ${ex.getCause}") *>
          ZIO.succeed(false)
      )
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
  val live: ZLayer[AppConfig with AskQueueService with DatabaseService, Throwable, TelegramService] =
    ZLayer.scoped {
      for {
        config  <- ZIO.service[AppConfig].map(_.telegram)
        started <- Ref.Synchronized.make(false)
        queue   <- ZIO.service[AskQueueService]
        db      <- ZIO.service[DatabaseService]
        service  = new TelegramServiceImpl(config, started, queue.askQ, db)
      } yield service
    }
}
