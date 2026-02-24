package service

import model.Ask
import zio.{ Queue, ZLayer }

trait AskQueueService {
  def askQ: Queue[Ask]
}

object AskQueueService {
  val live: ZLayer[Any, Nothing, AskQueueService] =
    ZLayer.scoped {
      Queue.unbounded[Ask].map(q => new AskQueueService { val askQ: Queue[Ask] = q })
    }
}
