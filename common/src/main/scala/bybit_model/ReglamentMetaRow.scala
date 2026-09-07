package bybit_model

case class ReglamentMetaRow(
                             id:         Int,
                             table_name: String,
                             ts_column:  String,
                             keep_days:  Double,
                             enabled:    Boolean
                           )