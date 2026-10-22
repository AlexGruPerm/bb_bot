package model

import app.UserId
import com.bot4s.telegram.models.User

trait Ask {
  def cmd: String
}

final case class GetCommonBalance(user: User) extends Ask {
  override def cmd: String = "/getCommonBalance"
}

final case class GetSymbolsBalance(user: User) extends Ask {
  override def cmd: String = "/getSymbolsBalance"
}

final case class HelpFrom(user: User) extends Ask {
  override def cmd: String = "/help"
}

final case class GetViewDeep(interval: String, deep_bars: Int, user: User) extends Ask {
  override def cmd: String = s"/getViewDeep $interval $deep_bars"
}

final case class GetViewDeepInvalid(args: String, user: User) extends Ask {
  override def cmd: String = s"/getViewDeep $args"
}

/** Sends admin error alerts (from data.common_log) to all active admins. */
final case class SendAdminErrorLog(message: String) extends Ask {
  override def cmd: String = "/sendAdminErrorLog"
}

/** Broadcasts an LLM advice: buy/sell go out with the explanation, hold only as a short note. */
final case class LlmFuturesMetricsAdvice(adviceId: Int, advice: String, adviceText: String) extends Ask {
  override def cmd: String = "/llm_fut_metr_advice"
}

/** Returns the explanation (advice_text) of a stored LLM advice by its id, requested by a user. */
final case class GetLlmAdviceDescr(adviceId: Int, user: User) extends Ask {
  override def cmd: String = s"/get_hold_descr $adviceId"
}

/** Invalid /get_hold_descr invocation (bad or missing argument). */
final case class GetLlmAdviceDescrInvalid(args: String, user: User) extends Ask {
  override def cmd: String = s"/get_hold_descr $args"
}
