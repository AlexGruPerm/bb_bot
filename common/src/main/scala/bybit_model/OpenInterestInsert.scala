package bybit_model

case class OpenInterestInsert(
  id_symbol: Int,
  ts_bybit: Long,
  oi: Double
)

object OpenInterestResConverter {

  def oiResToOpenInterestInsert(symbol: Symbol, openInterest: OpenInterestResult): List[OpenInterestInsert] =
    openInterest.list.foldLeft(List.empty[OpenInterestInsert]) { case (r, c) =>
      r :+ OpenInterestInsert(symbol.id, c.timestamp, c.openInterest)
    }

}
