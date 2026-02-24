package bybit

import zio.ZIO

object ApiDecode {
  def unwrap[A](e: Either[Throwable, Either[String, A]]): ZIO[Any, Exception, A] =
    ZIO
      .fromEither(e)
      .mapError {
        case ex: Exception => ex
        case t             => new Exception(t)
      }
      .flatMap {
        case Left(msg)  => ZIO.fail(new Exception(msg))
        case Right(res) => ZIO.succeed(res)
      }
}
