package bybit_model

import bybit_model.Types.SymbolId

case class SymbolAdviceProc(
  adviserId: Int,
  idSymbol: SymbolId,
  procedureName: String,
  mins: Int
)
