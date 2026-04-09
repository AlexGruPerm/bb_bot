package services

import bybit_model.Symbol
import bybit_model.Types.{ SymbolCode, SymbolId }
import zio.{ IO, Ref, UIO, ZIO, ZLayer }

sealed trait SymbolsServiceError extends Throwable
case object NotFoundInSymbols    extends SymbolsServiceError

trait SymbolsService {
  def addSymbols(symbols: Set[Symbol]): UIO[Unit]
  def findById(id: Int): UIO[Option[Symbol]]
  def findSymbolById(id: SymbolId): IO[SymbolsServiceError, Symbol]
  def findByCode(code: String): UIO[Option[Symbol]]
  def getSymbols(): UIO[Set[Symbol]]
  def findSymbolByKLineTopic(klineTopic: String): ZIO[Any, Exception, Symbol]
}

case class SymbolsServiceImpl(ref: Ref[Set[Symbol]]) extends SymbolsService {

  // expect exactly this format "kline.{interval}.{symbol}"
  private val Pat = """^kline\.([^.]+)\.([^.]+)$""".r

  def addSymbols(symbols: Set[Symbol]): UIO[Unit] =
    ref.update(_ ++ symbols)

  def findById(id: SymbolId): UIO[Option[Symbol]] =
    ref.get.map(_.find(_.id == id))

  def findSymbolById(id: SymbolId): IO[SymbolsServiceError, Symbol] =
    ref.get.flatMap { all =>
      ZIO.fromOption(all.find(_.id == id)).orElseFail(NotFoundInSymbols)
    }

  def findByCode(code: SymbolCode): UIO[Option[Symbol]] =
    ref.get.map(_.find(_.code == code))

  def getSymbols(): UIO[Set[Symbol]] =
    ref.get

  def findSymbolByKLineTopic(klineTopic: String): ZIO[Any, Exception, Symbol] = for {
    codeTopic <- ZIO
      .attempt(klineTopic match {
        case Pat(_, symbolCode) => symbolCode
        case _                  => throw new Exception(s"Invalid KLine topic: $klineTopic")
      })
      .catchAll(err => ZIO.logError(s"${err.getMessage}") *> ZIO.fail(new Exception(err.getMessage)))
    symbolOpt <- findByCode(codeTopic)
  } yield symbolOpt.get

}

object SymbolsService {
  def layer: ZLayer[Any, Nothing, SymbolsService] =
    ZLayer.fromZIO(
      Ref.make(Set.empty[Symbol]).map(SymbolsServiceImpl)
    )
}
