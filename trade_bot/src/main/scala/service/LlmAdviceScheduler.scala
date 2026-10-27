package service

import bybit_model.{ FuturesMetricsLlmRow, FuturesMetricsMeta, LlmPrompt }
import postgresql.{ DictChanges, DictEvent }
import zio.http.Client
import zio.{ durationInt, Fiber, Ref, Schedule, ZIO, ZLayer }

import java.sql.SQLException
import javax.sql.DataSource
import scala.util.matching.Regex

/**
 * Reads LLM prompt configurations (data.llm_prompts_futures_metrics) at start and runs one advice effect per prompt,
 * repeating every `executeEveryMins` minutes. When a prompt changes (bb_dict_changed notification on the table) the
 * whole config is reloaded: running effects are interrupted and restarted with fresh parameters.
 *
 * On each run: read the futures metrics snapshot, render it as CSV, join it with the configured prompt text, call the
 * LLM, parse the advice, persist llm_advice + llm_advice_source_response, mark it as sent and enqueue an Ask for the
 * Telegram broadcast.
 */
trait LlmAdviceScheduler {
  def start: ZIO[DataSource with Client, Nothing, Unit]
}

final class LlmAdviceSchedulerLive(
  db: DatabaseService,
  dc: DictChanges,
  llm: LlmClient,
  ask: AskQueueService
) extends LlmAdviceScheduler {

  import LlmAdviceScheduler._

  private[this] val changeTables = Set("llm_prompts_futures_metrics")

  private def loadPrompts: ZIO[DataSource, SQLException, List[LlmPrompt]] =
    db.getLlmPrompts.map(_.filter(_.isEnabled))

  /** Renders the snapshot rows as `;`-separated CSV with a header row, one line per snapshot row. */
  private def renderCsv(rows: List[FuturesMetricsLlmRow]): String = {
    val header =
      "ts_db;rn;id_futures_metrics_groups;grp_code;cnt;pct_of_analyzed_by_count;total_oi_usdt;prcnt_oi"
    val body   = rows.map { r =>
      List(
        r.tsDb.toString,
        r.rn.toString,
        r.idFuturesMetricsGroups.toString,
        r.grpCode,
        r.cnt.map(_.toString).getOrElse(""),
        r.pctOfAnalyzedByCount.map(_.toString).getOrElse(""),
        r.totalOiUsdt.map(_.toString).getOrElse(""),
        r.prcntOi.map(_.toString).getOrElse("")
      ).mkString(";")
    }
    (header :: body).mkString("\n")
  }

  private def selectPromptText(prompt: LlmPrompt): ZIO[Any, Throwable, String] =
    prompt.promptLang match {
      case "rus" =>
        ZIO
          .fromOption(prompt.promptRu.filter(_.nonEmpty))
          .orElseFail(
            new RuntimeException(s"LLM prompt ${prompt.id}: prompt_ru is empty for lang=rus")
          )
      case "eng" =>
        ZIO
          .fromOption(prompt.promptEn.filter(_.nonEmpty))
          .orElseFail(
            new RuntimeException(s"LLM prompt ${prompt.id}: prompt_en is empty for lang=eng")
          )
      case other =>
        ZIO.fail(new RuntimeException(s"LLM prompt ${prompt.id}: unsupported prompt_lang=$other"))
    }

  /**
   * Full pipeline for one prompt. Any failure is caught and logged; when there is a raw response it is stored with a
   * null llm_advice_id so parsing/other errors remain visible.
   */
  private def runOnce(prompt: LlmPrompt): ZIO[DataSource with Client, Nothing, Unit] = {
    val effect = ZIO.scoped {
      for {
        meta      <- loadMeta(prompt)
        rows      <- db.getFuturesMetricsLlmSnapshot(prompt.idFuturesMetricsMeta)
        promptTxt <- selectPromptText(prompt)
        csv        = renderCsv(rows)
        request    = buildRequest(promptTxt, csv)
        response  <- llm.complete(request)
        _         <- persist(prompt, meta, request, response.content, response.rawResponse)
      } yield ()
    }
    effect.catchAll {
      case e: SQLException =>
        ZIO.logError(s"LLM advice (prompt ${prompt.id}) DB error: ${e.getMessage}")
      case e: Throwable    =>
        ZIO.logError(s"LLM advice (prompt ${prompt.id}) failed: ${e.getMessage}") *>
          db.insertLlmAdviceSourceResponse(None, s"prompt_id=${prompt.id}", s"error: ${e.getMessage}")
            .catchAll(err => ZIO.logError(s"Cannot log LLM failure: ${err.getMessage}"))
    }
  }

  /**
   * Loads the futures metrics meta of the prompt (data.futures_metrics_meta); its compare interval / execution period
   * are shown to the users in the advice header, so a missing row is a configuration error.
   */
  private def loadMeta(prompt: LlmPrompt): ZIO[DataSource, Throwable, FuturesMetricsMeta] =
    db.getFuturesMetricsMetaById(prompt.idFuturesMetricsMeta).flatMap {
      case Some(meta) => ZIO.succeed(meta)
      case None       =>
        ZIO.fail(
          new RuntimeException(
            s"LLM prompt ${prompt.id}: futures_metrics_meta ${prompt.idFuturesMetricsMeta} not found"
          )
        )
    }

  private def persist(
    prompt: LlmPrompt,
    meta: FuturesMetricsMeta,
    request: String,
    response: String,
    rawResponse: String
  ): ZIO[DataSource, Throwable, Unit] =
    parseAdvice(response) match {
      case Some((advice, adviceText)) =>
        for {
          id <- db.insertLlmAdvice(prompt.id, advice, adviceText)
          _  <- db.insertLlmAdviceSourceResponse(Some(id), request, rawResponse)
          _  <- db.markLlmAdviceSent(id)
          _  <- ask
            .askQ
            .offer(
              model.LlmFuturesMetricsAdvice(
                id,
                prompt.id,
                meta.compareIntervalMin,
                meta.executeEveryMins,
                advice,
                adviceText
              )
            )
        } yield ()
      case None                       =>
        db.insertLlmAdviceSourceResponse(None, request, rawResponse)
    }

  private def scheduled(prompt: LlmPrompt): ZIO[DataSource with Client, Nothing, Unit] =
    runOnce(prompt)
      .repeat(Schedule.spaced(prompt.executeEveryMins.minutes))
      .unit

  private def restart(running: Ref[Set[Fiber.Runtime[Any, Unit]]]): ZIO[DataSource with Client, Nothing, Unit] =
    for {
      _       <- ZIO.logInfo("(Re)loading LLM advice configuration")
      prompts <- loadPrompts.catchAll { e =>
        ZIO.logError(s"Cannot load LLM prompts: ${e.getMessage}") *> ZIO.succeed(List.empty)
      }
      _       <- running.get.flatMap(fs => ZIO.foreachDiscard(fs)(_.interrupt)).ignore
      _       <- running.set(Set.empty)
      fibers  <- ZIO.foreach(prompts)(p => scheduled(p).fork)
      _       <- running.set(fibers.toSet)
      _       <- ZIO.logInfo(s"LLM advice scheduler: ${prompts.size} effect(s) running")
    } yield ()

  private def reloadOnChange(running: Ref[Set[Fiber.Runtime[Any, Unit]]]): ZIO[DataSource with Client, Nothing, Unit] =
    dc.events
      .collect {
        case DictEvent.TableChanged(table) if changeTables.contains(table) => ()
        case DictEvent.Resync                                              => ()
      }
      .groupedWithin(Int.MaxValue, 3.seconds)
      .foreach(chunk => if (chunk.isEmpty) ZIO.unit else restart(running))

  override def start: ZIO[DataSource with Client, Nothing, Unit] =
    for {
      _       <- ZIO.logInfo("Starting LLM advice scheduler")
      running <- Ref.make[Set[Fiber.Runtime[Any, Unit]]](Set.empty)
      _       <- restart(running)
      _       <- reloadOnChange(running).forkDaemon
    } yield ()
}

object LlmAdviceScheduler {

  private val AdviceRegex: Regex = """\*\*(buy|sell|hold)\*\*""".r

  /** Renders the final user content: prompt text, then the CSV data, then the parsing instruction. */
  def buildRequest(promptText: String, csv: String): String =
    s"""$promptText
       |
       |$csv
       |
       |Ответь кратко, одним из слов: buy, sell, hold (оберни в ** ** чтобы я мог распарсить) и пара предложений почему такой совет.""".stripMargin

  /**
   * Extracts the advice token wrapped in ** ** (case-insensitive) and the remaining explanation text. Returns None when
   * no advice token is found.
   */
  def parseAdvice(response: String): Option[(String, String)] =
    AdviceRegex.findFirstMatchIn(response.toLowerCase).map { m =>
      val advice = response.substring(m.start + 2, m.end - 2).toLowerCase
      val text   = (response.substring(0, m.start) + response.substring(m.end)).trim
      (advice, text)
    }

  val live: ZLayer[DatabaseService with DictChanges with LlmClient with AskQueueService, Nothing, LlmAdviceScheduler] =
    ZLayer.fromFunction(new LlmAdviceSchedulerLive(_, _, _, _))
}
