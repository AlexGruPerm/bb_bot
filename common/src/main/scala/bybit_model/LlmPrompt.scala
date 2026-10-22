package bybit_model

/**
 * A prompt configuration for the LLM advice generation (data.llm_prompts_futures_metrics). Tied to one futures metrics
 * meta configuration, so the advice is generated over the snapshot of that metric. `promptLang` selects which prompt
 * text is used ('rus' -> promptRu, 'eng' -> promptEn).
 */
case class LlmPrompt(
  id: Int,
  idFuturesMetricsMeta: Int,
  promptEn: Option[String],
  promptRu: Option[String],
  promptLang: String,
  isEnabled: Boolean,
  executeEveryMins: Int
)
