package app

import bybit.ByBitService
import service.TraderService
import services.SymbolsService
import zio.{ durationInt, Schedule, UIO, ZIO }
import bybit_model.{ createdOrder, LimitTradeAdvice, MarketTradeAdvice, Symbol, TradeAdviceOrder }
import zio.json.EncoderOps

import java.sql.SQLException
import javax.sql.DataSource

object Trader {

  private def debugOpenOrderJson(m: MarketTradeAdvice): UIO[Unit] =
    ZIO.logDebug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~") *>
      ZIO.logDebug(m.toJsonPretty) *>
      ZIO.logDebug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")

  /*
  def trade(symbol: Symbol): ZIO[TraderService with ByBitService with DataSource with SymbolsService, Throwable, Unit] =
    for {
      tradeService         <- ZIO.service[TraderService]
      tradeAdviceSelectOpt <- tradeService.getTradeAdvice(symbol)
      tradeAdviceOpt        = tradeAdviceSelectOpt.map(_.toTradeAdvice)
      createdOrd           <- tradeAdviceOpt.fold[ZIO[ByBitService with DataSource with SymbolsService, Throwable, createdOrder]](
        ZIO.succeed(createdOrder.getEmpty)
      ) {
        case ta @ (m: MarketTradeAdvice) =>
          ZIO.logInfo(s"MARKET ADVICE ${m.side} FOR ${m.symbol}") *>
            debugOpenOrderJson(m) *>
            tradeService
              .openOrder(ta)
              .tapError(err => ZIO.logError(s"Error when openOrder - ${err.getMessage} "))
              .catchAll(err =>
                ZIO.logInfo(s"Error openOrder [catchAll] ${err.getMessage}") *> ZIO.succeed(createdOrder.getEmpty)
              ) // todo: save log in DB before ZIO.succeed(createdOrder.getEmpty)
        case _: LimitTradeAdvice         => ZIO.logInfo(s"This is LIMIT advice from DB.").as(createdOrder.getEmpty)
        case _                           => ZIO.logInfo("There is no MARKET or LIMIT advice.").as(createdOrder.getEmpty)
      }
      _                    <- ZIO.logInfo(s"orderId = ${createdOrd.orderId}").when(createdOrd.orderId != 0)
      _                    <- tradeService
        .saveTradeAdviceOrder(
          TradeAdviceOrder(
            orderId = createdOrd.orderId,
            id_trade_advice = tradeAdviceSelectOpt.map(_.trade_advice_id).getOrElse(0L),
            ts_bybit = createdOrd.time
          )
        )
        .when(createdOrd.orderId != 0L)
      _                    <- tradeService
        .getOrderHistoryInfo(createdOrd.orderId)
        .when(createdOrd.orderId != 0)
        .tapError(e => ZIO.logError(s"getOrderHistoryInfo attempt failed: ${e.getMessage}"))
        .retryOrElse(
          Schedule.recurs(3),
          (e: Throwable, _: Long) => ZIO.logWarning(s"give up after 3 retries: ${e.getMessage}") *> ZIO.none
        )
        .flatMap {
          case Some(hist) =>
            ZIO.logInfo(
              s"(${hist.side} -> [${hist.orderId}][${hist.symbol}] qty: ${hist.qty} avgpr: ${hist.avgPrice} XXX:${hist.extraFees}"
            ) *>
              tradeService.saveOrderHistory(order = hist)
          case None       => ZIO.unit
        }
      /*
    _ <- tradeService.getOrderHistoryInfo(createdOrd.orderId).when(createdOrd.orderId != 0).flatMap{
        history => history.fold[ZIO[ByBitService
                                with DataSource
                                with SymbolsService, SQLException, Unit]](ZIO.unit)(
          hist => ZIO.logInfo(s"(${hist.side} -> [${hist.orderId}][${hist.symbol}] qty: ${hist.qty} avgpr: ${hist.avgPrice} XXX:${hist.extraFees}") *>
            tradeService.saveOrderHistory(order = hist)
        )
      }
       */
    } yield ()

  */

}
