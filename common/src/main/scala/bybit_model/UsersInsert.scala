package bybit_model

case class UsersInsert(
  user_id: Long,
  is_bot: Boolean,
  first_name: String,
  last_name: Option[String],
  username: Option[String],
  language_code: Option[String],
  is_premium: Option[Boolean],
  added_to_attachment_menu: Option[Boolean],
  can_join_groups: Option[Boolean],
  can_read_all_group_messages: Option[Boolean],
  supports_inline_queries: Option[Boolean]
)
