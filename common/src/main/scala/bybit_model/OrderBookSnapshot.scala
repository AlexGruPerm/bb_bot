package bybit_model

case class OrderBookSnapshotInsert(
  id_symbol: Int,
  ts_bybit: Long
)

case class OrderBookSnapshot(
  id: Long,
  id_symbol: Int,
  ts_db: Long,
  ts_bybit: Long
)

object OrderBookResConverter {

  def obResToSnapshotInsert(symbol: Symbol, orderBook: OrderBookResult): OrderBookSnapshotInsert =
    OrderBookSnapshotInsert(id_symbol = symbol.id, ts_bybit = orderBook.ts)

}
