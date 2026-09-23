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
