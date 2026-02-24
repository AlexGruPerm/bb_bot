package bybit_model

import java.time.LocalDateTime

case class ReglamentLog(id: Long, id_reglament: Int, end_ts: Option[java.sql.Timestamp] = None, deleted_rows: Long)
