package bybit_model

/**
 * One row of the futures metrics snapshot fed to the LLM (see the ranked CTE over data.futures_data_price_oi_metrics).
 * `grpCode` is the decoded price/oi direction code from data.futures_metrics_groups (e.g. "price up oi down").
 */
case class FuturesMetricsLlmRow(
  tsDb: java.time.LocalDateTime,
  rn: Long,
  idFuturesMetricsGroups: Int,
  grpCode: String,
  cnt: Option[Long],
  pctOfAnalyzedByCount: Option[BigDecimal],
  totalOiUsdt: Option[BigDecimal],
  prcntOi: Option[BigDecimal]
)
