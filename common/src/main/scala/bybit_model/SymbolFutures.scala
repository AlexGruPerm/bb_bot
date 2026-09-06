package bybit_model

import bybit_model.Types.{CoinId, SymbolCode, SymbolId}

case class SymbolFutures(
                          id: SymbolId,
                          code: SymbolCode,
                          isObserved: Boolean
                        )
