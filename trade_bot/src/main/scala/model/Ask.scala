package model

import com.bot4s.telegram.models.User

trait Ask {
  def cmd: String
}

object GetCommonBalance extends Ask {
  override def cmd: String = "/getCommonBalance"
}

object GetSymbolsBalance extends Ask {
  override def cmd: String = "/getSymbolsBalance"
}

final case class HelpFrom(user: User) extends Ask {
  override def cmd: String = "/help"
}

final case class GetViewDeep(interval: String, deep_bars: Int) extends Ask {
  override def cmd: String = s"/getViewDeep $interval $deep_bars"
}

final case class GetViewDeepInvalid(args: String) extends Ask {
  override def cmd: String = s"/getViewDeep $args"
}

/** Sends admin error alerts (from data.common_log) to all active admins. */
final case class SendAdminErrorLog(message: String) extends Ask {
  override def cmd: String = "/sendAdminErrorLog"
}
