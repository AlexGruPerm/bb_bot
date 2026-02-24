package bybit

import bybit_model.{
  ApiRespCreateOrder,
  ApiRespOpenInterest,
  ApiRespOrderBook,
  ApiRespOrderHistInfo,
  ApiRespWalletBalance,
  KLine,
  KLineTopic,
  LimitTradeAdvice,
  MarketTradeAdvice,
  Ok,
  OpenInterestResult,
  OrderBookResult,
  SuccessSubscribeKLine,
  TradeAdvice
}
import conf.{ AppConfig, ByBitConfig }
import services.{ PingPongService, SymbolsService }
import zio.http.ChannelEvent.Read
import zio.http.{ Body, Client, Handler, Headers, Request, Response, URL, WebSocketChannel, WebSocketFrame }
import zio.{ durationInt, Clock, Fiber, Queue, RIO, Scope, Task, ZIO, ZLayer }
import zio.json.{ DecoderOps, EncoderOps }

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.annotation.nowarn
import bybit_model.Types.{ IntervalCode, OrderID, SymbolCode }

trait ByBitService {
  def getOrderBook(symbol: String): ZIO[Scope with Client, Throwable, OrderBookResult]
  def getOpenInterest(symbol: String): ZIO[Scope with Client, Throwable, OpenInterestResult]
  def getAndSaveBars(
    topics: Set[KLineTopic],
    c_interval: IntervalCode
  ): ZIO[KLineHandler with SymbolsService with PingPongService, Throwable, Unit]
  def getWalletBalance(): ZIO[Scope with Client, Throwable, ApiRespWalletBalance]
  def orderCreate(advice: TradeAdvice): ZIO[Scope with Client, Throwable, ApiRespCreateOrder]
  def getOrderHistInfo(orderId: OrderID): ZIO[Scope with Client, Throwable, ApiRespOrderHistInfo]
}

class ByBitServiceImpl(config: ByBitConfig) extends ByBitService {

  private val bbUrl: String = "https://api.bybit.com"

  private val limit_order_book: Int    = config.limit_order_book
  private val interval_oi: String      = config.interval_oi
  private val limit_open_interest: Int = config.limit_open_interest
  private val recvWindow: String       = config.recvWindow

  private val wsUrlSpot = "wss://stream.bybit.com/v5/public/spot"

  private val bbUrl_Create_Order: String       = s"$bbUrl/v5/order/create"
  private val bbUrl_Order_History_Info: String = s"$bbUrl/v5/order/history"
  private val bbUrl_Position_info: String      = s"$bbUrl/v5/position/list"

  private def decodeUrl(url: String): ZIO[Any, IllegalArgumentException, URL] =
    ZIO.fromEither(URL.decode(url)).orElseFail(new IllegalArgumentException(s"Invalid URL: $url"))

  private case class PairedUrl(signUrl: String, apiUrl: String) {
    def decodedUrl: ZIO[Any, IllegalArgumentException, URL] =
      ZIO.fromEither(URL.decode(apiUrl)).orElseFail(new IllegalArgumentException(s"Invalid URL: $apiUrl"))
  }

  private def getPairUrl(endpoint: ByBitEndpoint, symbol: SymbolCode): PairedUrl = endpoint match {
    case OrderBook    =>
      PairedUrl(
        signUrl = s"orderbook?category=spot&symbol=$symbol&limit=$limit_order_book",
        apiUrl = s"$bbUrl/v5/market/orderbook?category=spot&symbol=$symbol&limit=$limit_order_book"
      )
    case OpenInterest =>
      PairedUrl(
        signUrl = s"open-interest?category=inverse&symbol=$symbol&intervalTime=$interval_oi&limit=$limit_open_interest",
        apiUrl =
          s"$bbUrl/v5/market/open-interest?category=inverse&symbol=$symbol&intervalTime=$interval_oi&limit=$limit_open_interest"
      )
  }

  private val pairUrlWb: PairedUrl =
    PairedUrl(
      signUrl = s"accountType=UNIFIED",
      apiUrl = s"$bbUrl/v5/account/wallet-balance?accountType=UNIFIED"
    )

  private def headerWithSignature(ts: String, sign: String): Headers =
    Headers
      .empty
      .addHeader("X-BAPI-API-KEY", config.key)
      .addHeader("X-BAPI-SIGN", sign)
      .addHeader("X-BAPI-SIGN-TYPE", "2")
      .addHeader("X-BAPI-TIMESTAMP", ts)
      .addHeader("X-BAPI-RECV-WINDOW", recvWindow)
      .addHeader("Content-Type", "application/json")

  private def sendChatMessage(message: String): ZIO[Queue[String], Throwable, Unit] =
    ZIO.serviceWithZIO[Queue[String]](_.offer(message).unit)

  private def processQueue(channel: WebSocketChannel): ZIO[Queue[String], Throwable, Unit] = {
    for {
      queue <- ZIO.service[Queue[String]]
      msg   <- queue.take
      _     <- channel.send(Read(WebSocketFrame.Text(msg)))
    } yield ()
  }.forever.forkDaemon.unit

  private def respSubscribe(src: String): ZIO[Any, Nothing, Unit] =
    ZIO
      .fromEither(src.fromJson[SuccessSubscribeKLine])
      .foldZIO(
        e => ZIO.logError(s"Subscribe parse failed: $e | $src"),
        a => ZIO.logInfo(s"****** SUBSCRIBED : ${a.conn_id} ******")
      )

  private def respKline(src: String): ZIO[SymbolsService with KLineHandler, Nothing, Unit] =
    ZIO
      .fromEither(src.fromJson[KLine])
      .foldZIO(
        e => ZIO.logError(s"Kline parse failed: $e | $src"),
        kline =>
          for {
            symbol  <- ZIO.serviceWithZIO[SymbolsService](_.findSymbolByKLineTopic(kline.topic))
            handler <- ZIO.service[KLineHandler]
            _       <- handler.handle(kline, symbol)
          } yield ()
      )
      .catchAll(e => ZIO.logInfo(s"ERROR, CatchAll - ${e.getMessage}") *> ZIO.unit)

  private def respPong(src: String, c_interval: String): ZIO[PingPongService, Nothing, Unit] =
    ZIO
      .fromEither(src.fromJson[Pong])
      .foldZIO(
        e => ZIO.logError(s"Pong parse failed: $e | $src"),
        _ =>
          for {
            pps <- ZIO.service[PingPongService]
            pp  <- pps.get(c_interval)
            now <- Clock.currentTime(TimeUnit.SECONDS)
            _   <- ZIO.when(pp.fold(true)(_.state == Ok))(
              pps.savePong(c_interval, pong_ts = now)
            )
          } yield ()
      )

  private def parseResponseJson(
    src: String,
    c_interval: IntervalCode
  ): ZIO[KLineHandler with SymbolsService with PingPongService, Nothing, Any] =
    if (src.contains(""""op":"subscribe""""))
      respSubscribe(src)
    else if (src.contains(""""topic":"kline."""))
      respKline(src)
    else if (src.contains(""""ret_msg":"pong"""))
      respPong(src, c_interval)
    else
      ZIO.logInfo(s"Ignored: $src").ignore

  private def periodPing(channel: WebSocketChannel, c_interval: IntervalCode): ZIO[PingPongService, Nothing, Unit] =
    for {
      _   <- ZIO.sleep(10.seconds)
      now <- Clock.currentTime(TimeUnit.SECONDS)
      pps <- ZIO.service[PingPongService]
      pp  <- pps.get(c_interval)
      _   <- ZIO.when(pp.fold(true)(_.state == Ok))(
        channel.send(Read(WebSocketFrame.text(Ping().toJson))).ignore *>
          pps.savePing(c_interval, ping_ts = now)
      )
    } yield ()

  private def webSocketHandlerSpot(c_interval: IntervalCode): ZIO[ByBitSocketHandler, Throwable, Response] =
    Handler.webSocket { channel =>
      for {
        _ <- processQueue(channel)
        _ <- periodPing(channel, c_interval).forever.fork
        _ <- channel.receiveAll {
          case Read(WebSocketFrame.Text(responseJson)) => parseResponseJson(responseJson, c_interval)
          case _                                       => ZIO.unit
        }
      } yield ()
    }.connect(wsUrlSpot)
      .tapError(e => ZIO.logError(s"Error after webSocketHandlerSpot ${e.getClass.getName} - ${e.getMessage}"))

  private def sign(timestamp: String, query: String): String = {
    val payload = new StringBuilder(timestamp)
      .append(config.key)
      .append(recvWindow)
      .append(query)
      .toString
    val mac     = Mac.getInstance("HmacSHA256")
    val keySpec = new SecretKeySpec(config.secret.getBytes, "HmacSHA256")
    mac.init(keySpec)
    val hash    = mac.doFinal(payload.getBytes)
    hash.map("%02x".format(_)).mkString
  }

  private def getSignature(timestamp: String, query: String): Task[String] = ZIO.attempt(
    sign(
      timestamp = timestamp,
      query = query
    )
  )

  override def getOrderBook(symbol: SymbolCode): ZIO[Scope with Client, Throwable, OrderBookResult] = for {
    ts                   <- ZIO.succeed(Instant.now().toEpochMilli.toString)
    pairUrl              <- ZIO.succeed(getPairUrl(OrderBook, symbol))
    bbSignature          <- getSignature(ts, pairUrl.signUrl)
    decodedApiUrl        <- pairUrl.decodedUrl
    headersWithSign       = Headers(headerWithSignature(ts, bbSignature))
    response             <- ZIO.serviceWithZIO[Client](_.request(Request.get(decodedApiUrl).copy(headers = headersWithSign)))
    orderBookSrcJsonData <- response.body.asString
    parsedOrderBook      <- ZIO.attempt(orderBookSrcJsonData.fromJson[ApiRespOrderBook]).either
    res                  <- ApiDecode.unwrap(parsedOrderBook)
  } yield res.result

  override def getWalletBalance(): ZIO[Scope with Client, Throwable, ApiRespWalletBalance] = for {
    ts                       <- ZIO.succeed(Instant.now().toEpochMilli.toString)
    pairUrl                  <- ZIO.succeed(pairUrlWb)
    bbSignature              <- getSignature(ts, pairUrl.signUrl)
    decodedApiUrl            <- pairUrl.decodedUrl
    headersWithSign           = Headers(headerWithSignature(ts, bbSignature))
    response                 <- ZIO.serviceWithZIO[Client](_.request(Request.get(decodedApiUrl).copy(headers = headersWithSign)))
    walletBalanceSrcJsonData <- response.body.asString
    parsedWalletBalance      <- ZIO.attempt(walletBalanceSrcJsonData.fromJson[ApiRespWalletBalance]).either
    res                      <- ApiDecode.unwrap(parsedWalletBalance)
  } yield res

  override def getOpenInterest(symbol: SymbolCode): ZIO[Scope with Client, Throwable, OpenInterestResult] = for {
    ts                      <- ZIO.succeed(Instant.now().toEpochMilli.toString)
    pairUrl                 <- ZIO.succeed(getPairUrl(OpenInterest, symbol))
    bbSignature             <- getSignature(ts, pairUrl.signUrl)
    decodedApiUrl           <- pairUrl.decodedUrl
    headersWithSign          = Headers(headerWithSignature(ts, bbSignature))
    response                <- ZIO.serviceWithZIO[Client](_.request(Request.get(decodedApiUrl).copy(headers = headersWithSign)))
    openInterestSrcJsonData <- response.body.asString
    parsedOpenInterest      <- ZIO.attempt(openInterestSrcJsonData.fromJson[ApiRespOpenInterest]).either
    res                     <- ApiDecode.unwrap(parsedOpenInterest)
  } yield res.result

  @nowarn("msg=dead code")
  def getAndSaveBars(
    topics: Set[KLineTopic],
    c_interval: IntervalCode
  ): ZIO[KLineHandler with SymbolsService with PingPongService, Throwable, Unit] =
    ZIO
      .scoped(for {
        _                 <- webSocketHandlerSpot(c_interval)
        subscribeCoinsJson = SubscribeCoins(
          op = "subscribe",
          args = topics.map(klt => klt.getTopicString)
        )
        jsonSubscribeCoins = subscribeCoinsJson.toJson
        _                 <- sendChatMessage(jsonSubscribeCoins)
        _                 <- ZIO.never
      } yield ())
      .provideSomeLayer[KLineHandler with SymbolsService with PingPongService](
        Client.default ++ ZLayer(Queue.bounded[String](100))
      )

  override def orderCreate(advice: TradeAdvice): ZIO[Scope with Client, Throwable, ApiRespCreateOrder] = for {
    ts                <- ZIO.succeed(Instant.now().toEpochMilli.toString)
    bodyStr            = advice match {
      case l: LimitTradeAdvice  => l.toJson
      case m: MarketTradeAdvice => m.toJson
    }
    bbSignature       <- getSignature(ts, bodyStr)
    _                 <- ZIO.logInfo(s"URL = $bbUrl_Create_Order")
    urlCreateOrder    <- decodeUrl(s"$bbUrl_Create_Order")
    headersWithSign    = Headers(headerWithSignature(ts, bbSignature))
    req                = Request.post(urlCreateOrder, Body.fromString(bodyStr)).addHeaders(headersWithSign)
    response          <- ZIO.serviceWithZIO[Client](_.request(req))
    src               <- response.body.asString
    _                 <- ZIO.logInfo(s"Status=${response.status}, Body=$src")
    parsedCreateOrder <- ZIO.attempt(src.fromJson[ApiRespCreateOrder]).either
    res               <- ApiDecode.unwrap(parsedCreateOrder)
  } yield res

  def getOrderHistInfo(orderId: OrderID): ZIO[Scope with Client, Throwable, ApiRespOrderHistInfo] = for {
    ts                      <- ZIO.succeed(Instant.now().toEpochMilli.toString)
    bbSignature             <- getSignature(ts, s"category=spot&orderId=$orderId")
    url                     <- decodeUrl(s"$bbUrl_Order_History_Info?category=spot&orderId=$orderId")
    headersWithSign          = Headers(headerWithSignature(ts, bbSignature))
    response                <- ZIO.serviceWithZIO[Client](_.request(Request.get(url).copy(headers = headersWithSign)))
    orderHistorySrcJsonData <- response.body.asString
    parsedOrderHistory      <- ZIO.attempt(orderHistorySrcJsonData.fromJson[ApiRespOrderHistInfo]).either
    res                     <- ApiDecode.unwrap(parsedOrderHistory)
  } yield res

}

object ByBitService {
  val live: ZLayer[AppConfig, Nothing, ByBitService] =
    ZLayer {
      for {
        appConfig <- ZIO.service[AppConfig]
      } yield new ByBitServiceImpl(appConfig.bybitAccount)
    }
}
