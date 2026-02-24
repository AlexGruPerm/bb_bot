package app

import bybit.ByBitService
import bybit_model.ErrorLog
import bybit_model.Types.LogLevelId
import io.netty.handler.codec.CodecException
import service.GatherService
import services.CoinService
import zio.ZIO

import java.net.{ ConnectException, NoRouteToHostException, UnknownHostException }
import java.sql.SQLException
import javax.sql.DataSource

object ErrorHandlers {

  def logPF(logLevelId: LogLevelId, module: String, action: String)(implicit
    funcSaveLogDb: ErrorLog => ZIO[GatherDataSourceEnv, SQLException, Unit]
  ): PartialFunction[Throwable, ZIO[GatherDataSourceEnv, Exception, Unit]] = {

    val errHandler: PartialFunction[Throwable, ErrorLog] = {
      case e: UnknownHostException   => ErrorLog(logLevelId, module, action, "UnknownHostException", e.getMessage)
      case e: NoRouteToHostException => ErrorLog(logLevelId, module, action, "NoRouteToHostException", e.getMessage)
      case e: ConnectException       => ErrorLog(logLevelId, module, action, "ConnectException", e.getMessage)
      case e: CodecException         => ErrorLog(logLevelId, module, action, "NettyCodec", e.getMessage)
      case e: SQLException           => ErrorLog(logLevelId, module, action, "SQLException", e.getMessage)
      case e: Throwable              => ErrorLog(logLevelId, module, action, e.getClass.getName, e.getMessage)
    }

    throwable: Throwable => {
      val errLog     = errHandler.applyOrElse(
        throwable,
        (t: Throwable) => ErrorLog(logLevelId, module, action, t.getClass.getName, t.getMessage)
      )
      val effLogSave =
        ZIO.logError(s"[${errLog.bb_module}:${errLog.bb_action}] Class = ${errLog.error_class} - ${errLog.msg}") *>
          funcSaveLogDb(errLog)
      effLogSave
    }
  }

}
