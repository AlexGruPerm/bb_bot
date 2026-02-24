package bybit_model

import bybit_model.Types.SymbolId

case class SymbolsAdviceProc(
  adviserId: Int,
  func: String,
  mins: Int,
  idSymbols: Set[SymbolId]
)
