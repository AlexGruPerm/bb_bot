package bybit_model

case class FuturesMetrics(
  id: Int,
  metricName: String,
  metricTable: String
)

/**
 * Configured run of a metric (data.futures_metrics_meta): how often it executes and which analysis parameters are fed
 * into the metric's queries. One metric (idFuturesMetrics) may have several configurations.
 */
case class FuturesMetricsMeta(
  id: Int,
  executeEveryMins: Int,
  idFuturesMetrics: Int,
  filterWindowMin: Int,
  compareIntervalMin: Int,
  filterTotalOi: BigDecimal,
  priceChangeThreshold: BigDecimal,
  oiChangeThreshold: BigDecimal
)
