package service

import zio.http._
import zio.test.Assertion._
import zio.test._
import zio.{ Scope, ZIO, ZLayer }

/**
 * Verifies that [[LlmClient.complete]] keeps the full JSON body returned by the LLM API, so
 * data.llm_advice_source_response.llm_response can be used for token-usage analysis, while the advice is parsed from
 * the assistant message content only.
 */
object LlmClientSpec extends ZIOSpecDefault {

  private val cannedResponse: String =
    """{"id":"chatcmpl-1","object":"chat.completion","model":"deepseek-chat","created":1,"choices":[{"index":0,"message":{"role":"assistant","content":"**buy** because reasons"},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":5,"total_tokens":17}}"""

  private val route: RoutePattern[Unit] = Method.POST / "v1" / "chat" / "completions"

  private val stubRoutes: Routes[Any, Response] =
    Routes(route -> Handler.fromFunction[Request](_ => Response.json(cannedResponse)))

  def spec: Spec[TestEnvironment, Any] =
    suite("LlmClientSpec")(
      test("complete returns assistant content and keeps the full raw JSON response") {
        ZIO.scoped {
          (for {
            client <- Client.default.build.map(_.get[Client])
            port   <- Server.install(stubRoutes)
            cfg     = conf.AiConfig(
              url = s"http://127.0.0.1:$port/v1/chat/completions",
              model = "deepseek-chat",
              temperature = 0.2,
              max_tokens = 300,
              token = "test"
            )
            llm     = new LlmClient.LlmClientLive(cfg)
            out    <- llm.complete("prompt").provideSomeLayer[Scope](ZLayer.succeed(client))
          } yield assertTrue(
            out.content == "**buy** because reasons",
            out.rawResponse.contains("\"usage\""),
            out.rawResponse.contains("\"total_tokens\":17"),
            out.rawResponse.contains("chat.completion")
          )).provideSomeLayer[Scope](Server.defaultWithPort(18080))
        }
      }
    )
}
