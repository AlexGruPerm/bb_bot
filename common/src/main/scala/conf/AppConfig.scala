package conf

import zio.Config
import zio.config.magnolia.deriveConfig

case class AppConfig(
  bybitAccount: ByBitConfig,
  db: DbConfig,
  telegram: TelegramConfig
)

object AppConfig {
  val descriptor: Config[AppConfig] = deriveConfig[AppConfig]
}
