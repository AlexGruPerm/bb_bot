package service

import app.ByBitDsSymbol
import bybit.ByBitService
import bybit_model.{ createdOrder, OrderItemInfo, Symbol, TradeAdvice, TradeAdviceOrder, TradeAdviceSelect }
import bybit_model.Types.OrderID
import services.SymbolsService
import zio.{ ZIO, ZLayer }
import zio.http.Client

import java.sql.SQLException
import javax.sql.DataSource

trait TraderService {

  /**
   * Get last trade advice from db. (and mark it as taken)
   */
  //def getTradeAdvice(symbol: Symbol): ZIO[ByBitDsSymbol, Throwable, Option[TradeAdviceSelect]]
  //def saveTradeAdviceOrder(taOrder: TradeAdviceOrder): ZIO[ByBitDsSymbol, SQLException, Unit]
  def openOrder(advice: TradeAdvice): ZIO[ByBitDsSymbol, Throwable, createdOrder]
  def getOrderHistoryInfo(orderId: OrderID): ZIO[ByBitDsSymbol, Throwable, OrderItemInfo]
  def saveOrderHistory(order: OrderItemInfo): ZIO[ByBitDsSymbol, SQLException, Unit]
}

final class TraderServiceLive(db: DatabaseService) extends TraderService {

  private val client = Client.default

  /*
  override def getTradeAdvice(symbol: Symbol): ZIO[ByBitDsSymbol, Throwable, Option[TradeAdviceSelect]] =
    for {
      advice <- db.getTradeAdvice(symbol)
    } yield advice

  override def saveTradeAdviceOrder(taOrder: TradeAdviceOrder): ZIO[ByBitDsSymbol, SQLException, Unit] =
    for {
      _ <- ZIO.logInfo(taOrder.debug).when(taOrder.id_trade_advice != 0L)
      _ <- db.saveTradeAdviceOrder(taOrder)
    } yield ()
  */

  override def openOrder(advice: TradeAdvice): ZIO[ByBitDsSymbol, Throwable, createdOrder] =
    for {
      bbService <- ZIO.service[ByBitService]
      ord       <- ZIO.scoped(bbService.orderCreate(advice)).provide(client)
    } yield createdOrder(orderId = ord.result.orderId, time = ord.time)

  override def getOrderHistoryInfo(orderId: OrderID): ZIO[ByBitDsSymbol, Throwable, OrderItemInfo] =
    for {
      bbService  <- ZIO.service[ByBitService]
      ordHistory <- ZIO.scoped(bbService.getOrderHistInfo(orderId)).provide(client)
    } yield ordHistory.result.list.head



  override def saveOrderHistory(order: OrderItemInfo): ZIO[ByBitDsSymbol, SQLException, Unit] = for {
    _          <- ZIO.logInfo(s"TraderService.saveOrderHistory orderId = ${order.orderId}")
    _          <- ZIO.logInfo(order.toString)
    ordInfoIns <- ZIO
      .attempt(order.toOrderItemInfoInsert())
      .tapError { err =>
        ZIO.logError(s"ERROR converting toOrderItemInfoInsert, message = ${err.getMessage}")
      }
      .orDie
    _          <- db.saveOrderHistory(ordInfoIns)
  } yield ()

}

object TraderService {
  val live: ZLayer[DatabaseService, Nothing, TraderService] =
    ZLayer {
      for {
        db <- ZIO.service[DatabaseService]
      } yield new TraderServiceLive(db)
    }
}
