package bybit_model

import java.sql.Timestamp

case class Users(
  id: Int,
  user_id: Long,
  is_bot: Boolean,
  first_name: String,
  last_name: Option[String],
  username: Option[String],
  is_admin: Boolean,
  ts_db_begin: Timestamp,
  ts_db_end: Option[Timestamp]
) {
  def isActive(now: Timestamp): Boolean =
    !now.before(ts_db_begin) && ts_db_end.forall(end => !now.after(end))
}
