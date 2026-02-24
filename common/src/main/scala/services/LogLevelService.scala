package services

import bybit_model.LogLevel
import bybit_model.Types.{ LogLevelCode, LogLevelId }
import zio.{ IO, Ref, UIO, ZIO, ZLayer }

sealed trait LogLevelServiceError extends Throwable
case object NotFoundInLogLevel    extends LogLevelServiceError

trait LogLevelService {
  val getCount: UIO[Int]
  def add(levels: Set[LogLevel]): UIO[Unit]
  def findByCode(code: LogLevelCode): IO[LogLevelServiceError, LogLevel]
}

case class LogLevelServiceImpl(ref: Ref[Set[LogLevel]]) extends LogLevelService {

  override val getCount: UIO[LogLevelId] = ref.get.map(s => s.size)

  override def add(levels: Set[LogLevel]): UIO[Unit] = ref.update(r => r ++ levels)

  override def findByCode(code: LogLevelCode): IO[LogLevelServiceError, LogLevel] =
    ref.get.flatMap { all =>
      ZIO.fromOption(all.find(_.code == code)).orElseFail(NotFoundInLogLevel)
    }
}

object LogLevelService {
  def layer: ZLayer[Any, Nothing, LogLevelService] =
    ZLayer.fromZIO(
      Ref.make(Set.empty[LogLevel]).map(LogLevelServiceImpl)
    )
}
