import services.{ PingPongService, SymbolsService }
import zio.{ Queue, Scope }
import zio.http.Client

package object bybit {

  type ByBitSocketHandler =
    Queue[String] with Client with Scope with KLineHandler with SymbolsService with PingPongService

}
