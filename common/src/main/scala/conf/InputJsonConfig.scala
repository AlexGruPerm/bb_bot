package conf

import bybit_model.CustDbException.ErrInputException
import zio.{ ZIO, ZIOAppArgs }

import java.nio.file.Paths

object InputJsonConfig {
  def getInputJsonFilePath: ZIO[ZIOAppArgs, Throwable, String] = for {
    args           <- ZIO.service[ZIOAppArgs]
    _              <- ZIO.fail(ErrInputException).when(args.getArgs.isEmpty)
    currentDir     <- ZIO.attempt(Paths.get("").toAbsolutePath.toString + java.io.File.separator)
    configFileName <- args.getArgs.toList match {
      case List(configFile) => ZIO.attempt(configFile)
      case _                => ZIO.fail(ErrInputException)
    }
  } yield currentDir + configFileName
}
