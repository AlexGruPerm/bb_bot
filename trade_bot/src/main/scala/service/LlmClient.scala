package service

import conf.AppConfig
import zio.http.{ Body, Client, Header, Headers, MediaType, Request, URL }
import zio.json.{ DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder }
import zio.{ Scope, Task, ZIO, ZLayer }

/**
 * Result of one LLM call.
 *
 *   - `content` is the assistant message text (choices[0].message.content), used to parse the buy/sell/hold advice.
 *   - `rawResponse` is the complete JSON body returned by the API, stored in
 *     data.llm_advice_source_response.llm_response so consumed tokens (usage) can be analysed later.
 */
final case class LlmCompletion(content: String, rawResponse: String)

/**
 * Thin client for a chat-completions style LLM API (DeepSeek). Builds the request from AiConfig, sends prompt + data as
 * a single user message and returns both the parsed assistant text and the full raw response JSON. No retries: a
 * failure is propagated to the caller which logs it into data.llm_advice_source_response.
 */
trait LlmClient {
  def complete(userContent: String): ZIO[Scope with Client, Throwable, LlmCompletion]
}

object LlmClient {

  private final case class Message(role: String, content: String)
  private object Message     {
    implicit val encoder: JsonEncoder[Message] = DeriveJsonEncoder.gen[Message]
  }

  private final case class ChatRequest(
    model: String,
    messages: List[Message],
    temperature: Double,
    max_tokens: Int
  )
  private object ChatRequest {
    implicit val encoder: JsonEncoder[ChatRequest] = DeriveJsonEncoder.gen[ChatRequest]
  }

  private final case class ResponseMessage(content: String)
  private object ResponseMessage {
    implicit val decoder: JsonDecoder[ResponseMessage] = DeriveJsonDecoder.gen[ResponseMessage]
  }

  private final case class Choice(message: ResponseMessage)
  private object Choice {
    implicit val decoder: JsonDecoder[Choice] = DeriveJsonDecoder.gen[Choice]
  }

  private final case class ChatResponse(choices: List[Choice])
  private object ChatResponse {
    implicit val decoder: JsonDecoder[ChatResponse] = DeriveJsonDecoder.gen[ChatResponse]
  }

  final class LlmClientLive(cfg: conf.AiConfig) extends LlmClient {

    private def decodeUrl(url: String): Task[URL] =
      ZIO.fromEither(URL.decode(url)).orElseFail(new IllegalArgumentException(s"Invalid AI url: $url"))

    override def complete(userContent: String): ZIO[Scope with Client, Throwable, LlmCompletion] = for {
      url     <- decodeUrl(cfg.url)
      reqBody  = ChatRequest(
        model = cfg.model,
        messages = List(Message("user", userContent)),
        temperature = cfg.temperature,
        max_tokens = cfg.max_tokens
      ).toJson
      req      = Request
        .post(url, Body.fromString(reqBody))
        .addHeaders(
          Headers(
            Header.ContentType(MediaType.application.json),
            Header.Authorization.Bearer(cfg.token)
          )
        )
      resp    <- ZIO.serviceWithZIO[Client](_.request(req))
      src     <- resp.body.asString
      parsed  <- ZIO
        .fromEither(src.fromJson[ChatResponse])
        .mapError(err => new RuntimeException(s"LLM response parse error: $err; raw=$src"))
      content <- ZIO
        .fromOption(parsed.choices.headOption.map(_.message.content))
        .orElseFail(new RuntimeException(s"LLM response has no choices; raw=$src"))
    } yield LlmCompletion(content, src)
  }

  val live: ZLayer[AppConfig, Nothing, LlmClient] =
    ZLayer {
      for {
        appConfig <- ZIO.service[AppConfig]
      } yield new LlmClientLive(appConfig.ai)
    }
}
