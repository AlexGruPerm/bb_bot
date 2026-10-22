package db

import bybit_model.UsersInsert
import service.DatabaseService
import testsupport.{ DbTestFixtures, SqlSupport }
import zio.test.Assertion._
import zio.test._
import zio.{ Scope, ZIO }

/**
 * Integration tests for [[DatabaseService]] backed by a real PostgreSQL container with the production Liquibase
 * changelog. Tests share one container and one migrated schema, so each test cleans up what it writes.
 */
object DatabaseServiceSpec extends ZIOSpecDefault {

  private def db = ZIO.service[DatabaseService]

  private val sampleUser = UsersInsert(
    user_id = 900000001L,
    is_bot = false,
    first_name = "Test",
    last_name = Some("User"),
    username = Some("test_user"),
    language_code = Some("en"),
    is_premium = Some(true),
    added_to_attachment_menu = Some(false),
    can_join_groups = Some(true),
    can_read_all_group_messages = Some(false),
    supports_inline_queries = Some(false)
  )

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("DatabaseServiceSpec")(
      suite("isAvailiable")(
        test("returns true when database is reachable") {
          assertZIO(db.flatMap(_.isAvailiable))(isTrue)
        }
      ),
      suite("dictionaries")(
        test("getSymbols returns only enabled symbols as a set") {
          for {
            symbols <- db.flatMap(_.getSymbols)
          } yield assertTrue(
            symbols.nonEmpty,
            symbols.forall(_.is_enabled),
            symbols.map(_.code).contains("BTCUSDT")
          )
        },
        test("getCoins returns all seeded coins") {
          for {
            coins <- db.flatMap(_.getCoins)
          } yield assertTrue(
            coins.map(_.code).contains("BTC"),
            coins.map(_.code).contains("USDT"),
            coins.size >= 11
          )
        },
        test("getLogLevels returns seeded log levels") {
          for {
            levels <- db.flatMap(_.getLogLevels)
          } yield assertTrue(
            levels.map(_.code).contains("info"),
            levels.map(_.code).contains("error")
          )
        },
        test("refSymbolsIntervals returns intervals for requested symbol ids") {
          for {
            symbols <- db.flatMap(_.getSymbols)
            btc     <- ZIO.succeed(symbols.find(_.code == "BTCUSDT").get)
            refs    <- db.flatMap(_.refSymbolsIntervals(Set(btc.id)))
          } yield assertTrue(
            refs.nonEmpty,
            refs.forall(_.id_symbol == btc.id),
            refs.forall(_.kline_topic.startsWith("kline."))
          )
        },
        test("refSymbolsIntervals returns empty for unknown symbol ids") {
          for {
            refs <- db.flatMap(_.refSymbolsIntervals(Set(-1)))
          } yield assertTrue(refs.isEmpty)
        }
      ),
      suite("users")(
        test("upsertUser inserts a new user and getUsers returns it") {
          val cleanup = SqlSupport.cleanup(s"delete from data.users where user_id = ${sampleUser.user_id}")
          (for {
            _     <- cleanup
            _     <- db.flatMap(_.upsertUser(sampleUser))
            users <- db.flatMap(_.getUsers)
            found <- ZIO.succeed(users.find(_.user_id == sampleUser.user_id))
          } yield assertTrue(
            found.isDefined,
            found.get.first_name == sampleUser.first_name,
            found.get.username.contains("test_user"),
            found.get.is_bot == false
          )).ensuring(cleanup)
        },
        test("upsertUser updates an existing user on conflict of user_id") {
          val updated = sampleUser.copy(first_name = "Updated", username = Some("updated_user"))
          val cleanup = SqlSupport.cleanup(s"delete from data.users where user_id = ${sampleUser.user_id}")
          (for {
            _     <- cleanup
            _     <- db.flatMap(_.upsertUser(sampleUser))
            _     <- db.flatMap(_.upsertUser(updated))
            users <- db.flatMap(_.getUsers)
            found <- ZIO.succeed(users.filter(_.user_id == sampleUser.user_id))
          } yield assertTrue(
            found.size == 1,
            found.head.first_name == "Updated",
            found.head.username.contains("updated_user")
          )).ensuring(cleanup)
        }
      ),
      suite("logs")(
        test("saveLogInDb persists an error log and it is visible via common_log") {
          val token   = s"db-service-spec-${System.currentTimeMillis()}"
          val log     = bybit_model.ErrorLog(
            id_loglevel = 3,
            bb_module = "test",
            bb_action = "saveLogInDb",
            error_class = "SQLException",
            msg = token,
            for_admin = false
          )
          val cleanup = SqlSupport.cleanup(s"delete from data.common_log where msg = '$token'")
          (for {
            _     <- db.flatMap(_.saveLogInDb(log))
            count <- SqlSupport.queryLong(s"select count(*) from data.common_log where msg = '$token'")
          } yield assertTrue(count == 1L)).ensuring(cleanup)
        }
      ),
      suite("wallet balance")(
        test("getMaxWalletBalanceId returns 0 when no rows exist") {
          for {
            maxId <- db.flatMap(_.getMaxWalletBalanceId)
          } yield assertTrue(maxId >= 0L)
        }
      ),
      suite("advice")(
        test("getAdviceIntervals returns intervals bound to advice meta for enabled tradable symbols") {
          for {
            intervals <- db.flatMap(_.getAdviceIntervals)
          } yield assertTrue(intervals.nonEmpty, intervals.contains(15), intervals.contains(30))
        },
        test("getSymbolAdviceProcs returns procedures for a known interval") {
          for {
            procs <- db.flatMap(_.getSymbolAdviceProcs(15))
          } yield assertTrue(
            procs.nonEmpty,
            procs.forall(_.mins == 15),
            procs.map(_.procedureName).contains("bounce_turn")
          )
        },
        test("getSymbolAdviceProcs returns empty for an interval without advice config") {
          for {
            procs <- db.flatMap(_.getSymbolAdviceProcs(1))
          } yield assertTrue(procs.isEmpty)
        }
      ),
      suite("tg command log")(
        test("saveTgCommandLog persists a row referencing an existing user") {
          val userId  = sampleUser.user_id
          val cmd     = s"/test-${System.currentTimeMillis()}"
          val setup   =
            SqlSupport.execute(
              s"insert into data.users(user_id, first_name) values ($userId, 'Cmd') on conflict (user_id) do nothing"
            )
          val cleanup = SqlSupport.cleanup(
            s"delete from data.tg_command_log where id_user in (select id from data.users where user_id = $userId);" +
              s" delete from data.users where user_id = $userId"
          )
          (for {
            _     <- cleanup
            _     <- setup
            id    <- SqlSupport.queryLong(s"select id from data.users where user_id = $userId")
            _     <- db.flatMap(_.saveTgCommandLog(id.toInt, cmd))
            count <- SqlSupport.queryLong(
              s"select count(*) from data.tg_command_log where id_user = $id and command = '$cmd'"
            )
          } yield assertTrue(count == 1L)).ensuring(cleanup)
        }
      ),
      suite("users deactivation")(
        test("deactivateUser sets ts_db_end and reason for the telegram id") {
          val userId  = sampleUser.user_id
          val reason  = "bot was blocked by the user"
          val cleanup = SqlSupport.cleanup(s"delete from data.users where user_id = $userId")
          (for {
            _     <- cleanup
            _     <- SqlSupport.execute(
              s"insert into data.users(user_id, first_name) values ($userId, 'Blocked')"
            )
            _     <- db.flatMap(_.deactivateUser(userId, reason))
            count <- SqlSupport.queryLong(
              s"""select count(*) from data.users
                 | where user_id = $userId
                 |   and ts_db_end is not null
                 |   and reason = '$reason'""".stripMargin
            )
            users <- db.flatMap(_.getUsers)
          } yield assertTrue(count == 1L, !users.exists(_.user_id == userId))).ensuring(cleanup)
        }
      )
    ).provideShared(DbTestFixtures.databaseService) @@ TestAspect.sequential @@ TestAspect.withLiveClock

}
