package service

import app.{ ZioTlgDBs, ZioTlgDBsAdv }
import zio.{ durationInt, Cause, Fiber, Schedule, ZIO, ZLayer }
import bybit_model.{ Symbol, SymbolAdviceProc, SymbolsAdviceProc }

import java.sql.SQLException
import javax.sql.DataSource

trait AdvisorService {
  def runForInterval(mins: Int): ZIO[ZioTlgDBs, Throwable, Unit]
}

class AdvisorServiceImpl(tg: TelegramService, db: DatabaseService) extends AdvisorService {

  /**
   * List[SymbolAdviceProc] => List[SymbolsAdviceProc]
   *
   * Examples of List[SymbolAdviceProc]
   *
   * adviserid|idsymbol|procedurename|mins| ---------+--------+-------------+----+ 1| 1|bounce_turn | 5| 1|
   * 2|bounce_turn | 5| 1| 3|bounce_turn | 5| 1| 1|bounce_turn | 15| 1| 2|bounce_turn | 15| 1| 3|bounce_turn | 15| 2|
   * 4|bounce_outp | 30| 2| 5|bounce_outp | 30| 2| 6|bounce_outp | 30|
   *
   * adviserid, procedurename, mins listSymbols 1 bounce_turn 5 List(1,2,3) 1 bounce_turn 15 List(1,2,3) 2 bounce_outp
   * 30 List(4,5,6)
   */
  private def foldSymbolsAdviceProc(symbolAdviceProc: List[SymbolAdviceProc]): List[SymbolsAdviceProc] =
    symbolAdviceProc
      .groupBy(sap => (sap.adviserId, sap.procedureName, sap.mins))
      .view
      .mapValues(_.map(_.idSymbol))
      .map { case ((adviserid, func, mins), listSymbols) =>
        SymbolsAdviceProc(adviserid, func, mins, listSymbols.toSet)
      }
      .toList
      .sortBy(_.adviserId)

  override def runForInterval(mins: Int): ZIO[ZioTlgDBs, Throwable, Unit] =
    for {
      symbolAdviceProc                  <- db.getSymbolAdviceProcs(mins)
      sapFolded: List[SymbolsAdviceProc] = foldSymbolsAdviceProc(symbolAdviceProc)
      ids                               <- ZIO.foreachPar(sapFolded)(db.getAndSaveAdvice).withParallelism(10).map(_.flatten)
      savedAdvice                       <- db.getAllAdvice().when(ids.nonEmpty)
      _                                 <- ZIO.whenCase(savedAdvice) { case Some(listAdv) =>
        tg.sendNewAdvice(listAdv)
      }
    } yield ()

}

object AdvisorService {

  def runAdvisorForIntervals(intervals: List[Int]): ZIO[ZioTlgDBsAdv, Nothing, List[Fiber.Runtime[Any, Long]]] =
    ZIO.foreach(intervals) { i =>
      ZIO.serviceWithZIO[AdvisorService](
        _.runForInterval(i)
          .repeat(Schedule.spaced(i.minutes))
          .fork
      )
    }

  val live: ZLayer[TelegramService with DatabaseService, Nothing, AdvisorService] =
    ZLayer {
      for {
        tg <- ZIO.service[TelegramService]
        db <- ZIO.service[DatabaseService]
      } yield new AdvisorServiceImpl(tg, db)
    }

}
