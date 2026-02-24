package bybit_model

import bybit_model.Types.{ CoinCode, CoinId }

case class Coin(
  id: CoinId,
  code: CoinCode
)
