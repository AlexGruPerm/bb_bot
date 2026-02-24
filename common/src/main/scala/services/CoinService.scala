package services

import bybit_model.Coin
import bybit_model.Types.{ CoinCode, CoinId }
import zio.{ IO, Ref, UIO, ZIO, ZLayer }

sealed trait CoinServiceError extends Throwable
case object NotFoundInCoins   extends CoinServiceError

trait CoinService {
  def addCoins(coins: Set[Coin]): UIO[Unit]
  def getCoins(): UIO[Set[Coin]]
  def getIdByCode(coinCode: CoinCode): IO[CoinServiceError, Coin]
}

case class CoinServiceImpl(ref: Ref[Set[Coin]]) extends CoinService {

  override def addCoins(coins: Set[Coin]): UIO[Unit] =
    ref.update(_ ++ coins)

  override def getCoins(): UIO[Set[Coin]] = ref.get

  override def getIdByCode(coinCode: CoinCode): IO[CoinServiceError, Coin] =
    ref.get.flatMap { all =>
      ZIO.fromOption(all.find(_.code == coinCode)).orElseFail(NotFoundInCoins)
    }

}

object CoinService {
  def layer: ZLayer[Any, Nothing, CoinService] =
    ZLayer.fromZIO(
      Ref.make(Set.empty[Coin]).map(CoinServiceImpl)
    )
}
