package conf

case class AiConfig(
  url: String,
  model: String,
  temperature: Double,
  max_tokens: Int,
  token: String
) {
  override def toString: String =
    s"""
       |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       | AI:
       | url         : $url
       | model       : $model
       | temperature : $temperature
       | max_tokens  : $max_tokens
       | token       : ******
       |""".stripMargin
}
