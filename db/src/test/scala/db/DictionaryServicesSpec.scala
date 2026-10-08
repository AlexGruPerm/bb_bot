package db

import bybit_model.Coin
import services.{ CoinService, NotFoundInSymbols, SymbolsService }
import service.DatabaseService
import testsupport.DbTestFixtures
import zio.test.Assertion._
import zio.test._
import zio.ZIO

/**
 * Integration tests for the in-memory dictionary services [[SymbolsService]] and [[CoinService]].
 *
 * They reproduce the production startup flow: rows are read through [[DatabaseService]] and hydrated into the Ref-based
 * services, which are then queried the same way the application does. The shared service instances are reset at the
 * start of every test because they keep mutable [[zio.Ref]] state.
 */
object DictionaryServicesSpec extends ZIOSpecDefault {

  private def db = ZIO.service[DatabaseService]

  private val layer =
    DbTestFixtures.databaseService ++ SymbolsService.layer ++ CoinService.layer

  def spec: Spec[TestEnvironment, Any] =
    suite("DictionaryServicesSpec")(
      suite("CoinService")(
        test("hydrating from DB exposes every seeded coin and resolves by code") {
          for {
            service <- ZIO.service[CoinService]
            _       <- service.replace(Set.empty)
            coins   <- db.flatMap(_.getCoins)
            _       <- service.addCoins(coins)
            all     <- service.getCoins()
            btc     <- service.getIdByCode("BTC")
          } yield assertTrue(
            all == coins,
            btc.code == "BTC"
          )
        },
        test("getIdByCode fails for an unknown coin") {
          for {
            service <- ZIO.service[CoinService]
            _       <- service.replace(Set.empty)
            result  <- service.getIdByCode("NOPE").either
          } yield assertTrue(result.isLeft)
        },
        test("replace swaps the whole coin set") {
          val only = Set(Coin(1, "BTC"))
          for {
            service <- ZIO.service[CoinService]
            _       <- service.replace(Set.empty)
            _       <- service.addCoins(Set(Coin(2, "ETH")))
            _       <- service.replace(only)
            all     <- service.getCoins()
          } yield assertTrue(all == only)
        }
      ),
      suite("SymbolsService")(
        test("hydrating from DB exposes enabled symbols and resolves by id and code") {
          for {
            service <- ZIO.service[SymbolsService]
            _       <- service.replace(Set.empty)
            symbols <- db.flatMap(_.getSymbols)
            _       <- service.addSymbols(symbols)
            all     <- service.getSymbols()
            btc     <- ZIO.succeed(symbols.find(_.code == "BTCUSDT").get)
            byId    <- service.findSymbolById(btc.id)
            byCode  <- service.findByCode("BTCUSDT")
          } yield assertTrue(
            all == symbols,
            byId == btc,
            byCode.contains(btc)
          )
        },
        test("findSymbolById fails with NotFoundInSymbols for unknown id") {
          for {
            service <- ZIO.service[SymbolsService]
            _       <- service.replace(Set.empty)
            result  <- service.findSymbolById(-1).either
          } yield assertTrue(result == Left(NotFoundInSymbols))
        },
        test("findSymbolByKLineTopic parses the topic and resolves the symbol") {
          for {
            service <- ZIO.service[SymbolsService]
            _       <- service.replace(Set.empty)
            symbols <- db.flatMap(_.getSymbols)
            _       <- service.addSymbols(symbols)
            symbol  <- service.findSymbolByKLineTopic("kline.15.BTCUSDT")
          } yield assertTrue(symbol.code == "BTCUSDT")
        },
        test("findSymbolByKLineTopic fails on malformed topic") {
          for {
            service <- ZIO.service[SymbolsService]
            _       <- service.replace(Set.empty)
            result  <- service.findSymbolByKLineTopic("not-a-topic").either
          } yield assertTrue(result.isLeft)
        }
      )
    ).provideLayerShared(layer) @@ TestAspect.sequential

}
