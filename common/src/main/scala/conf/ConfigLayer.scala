package conf

import zio.{ Config, ZLayer }
import zio.config.typesafe.TypesafeConfigProvider

object ConfigLayer {
  def configLayer(jsonFilePath: String): ZLayer[Any, Config.Error, AppConfig] =
    ZLayer.fromZIO {
      TypesafeConfigProvider.fromHoconFile(new java.io.File(jsonFilePath)).load(AppConfig.descriptor)
    }
}
