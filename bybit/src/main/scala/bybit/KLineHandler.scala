package bybit

import bybit_model.Types.SymbolId
import bybit_model.KLine
import zio.{ UIO, ZIO, ZLayer }
import bybit_model.Symbol

import javax.sql.DataSource

trait KLineHandler {
  def handle(k: KLine, symbol: Symbol): UIO[Unit]
}

object KLineHandler {
  def live(f: (KLine, Symbol) => ZIO[DataSource, Exception, Unit]): ZLayer[DataSource, Nothing, KLineHandler] =
    ZLayer.fromFunction { ds: DataSource =>
      new KLineHandler {
        override def handle(k: KLine, symbol: Symbol): UIO[Unit] =
          f(k, symbol)
            .provide(ZLayer.succeed(ds))
            .catchAll(e => ZIO.logError(e.toString))
      }
    }
}
