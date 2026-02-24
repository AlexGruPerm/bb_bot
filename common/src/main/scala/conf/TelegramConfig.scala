package conf

case class TelegramConfig(
  token: String,
  webhook_port: Int,
  webhookUrl: String,
  keyStorePassword: String,
  pubcertpath: String,
  p12certpath: String,
  users: List[Long]
) {
  override def toString: String =
    s"""
       |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       | Telegram:
       | token        : ******
       | webhookUrl   : $webhookUrl
       | pubcertpath  : $pubcertpath
       | users count  : ${users.size}
       |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       |""".stripMargin
}
