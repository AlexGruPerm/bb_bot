package bybit_model

case class OrderBookResultInsert(
  id_snapshot: Long,
  side: String,
  price: Double,
  amount: Double
)

object OrderBookResultConverter {

  def obResToOrderBookResultInsert(id_snapshot: Long, orderBook: OrderBookResult): List[OrderBookResultInsert] =
    (orderBook.a.map(v => ("a", v)) ++ orderBook.b.map(v => ("b", v))).foldLeft(List.empty[OrderBookResultInsert]) {
      case (r, c) => r :+ OrderBookResultInsert(id_snapshot, c._1, c._2.price, c._2.amount)
    }

}
