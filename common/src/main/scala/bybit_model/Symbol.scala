package bybit_model

import bybit_model.Types.{ CoinCode, CoinId, SymbolCode, SymbolId }

case class Symbol(
  id: SymbolId,
  code: SymbolCode,
  coin: CoinCode,
  is_enabled: Boolean,
  is_tradable: Boolean
)

case class SymbolShort(
  id: SymbolId,
  code: SymbolCode
)

case class SymbolSource(
  id: SymbolId,
  code: SymbolCode,
  id_coin: CoinId,
  is_enabled: Boolean,
  is_tradable: Boolean
)
