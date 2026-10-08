package service

import bybit_model.UsersInsert
import conf.{ AppConfig, ByBitConfig, DbConfig, TelegramConfig }
import testsupport.{ DbTestFixtures, SqlSupport }
import zio.test.Assertion._
import zio.test._
import zio.{ Scope, ZIO, ZLayer }

import javax.sql.DataSource

/**
 * Integration tests for [[UsersService]] backed by the shared PostgreSQL test container.
 *
 * The service caches users in a [[zio.Ref]] and refreshes them from `data.users` through [[DatabaseService]]. Only the
 * DB-backed flow is exercised here; the periodic refresh loop is disabled in production.
 */
object UsersServiceSpec extends ZIOSpecDefault {

  private val userA = UsersInsert(
    user_id = 910000001L,
    is_bot = false,
    first_name = "Alice",
    last_name = Some("Admin"),
    username = Some("alice"),
    language_code = Some("en"),
    is_premium = Some(false),
    added_to_attachment_menu = None,
    can_join_groups = Some(true),
    can_read_all_group_messages = Some(false),
    supports_inline_queries = Some(false)
  )

  private val userB = UsersInsert(
    user_id = 910000002L,
    is_bot = false,
    first_name = "Bob",
    last_name = None,
    username = Some("bob"),
    language_code = Some("ru"),
    is_premium = Some(true),
    added_to_attachment_menu = None,
    can_join_groups = Some(true),
    can_read_all_group_messages = Some(false),
    supports_inline_queries = Some(false)
  )

  private val dbConfig = DbConfig(
    driver = "org.postgresql.Driver",
    url = "jdbc:postgresql://unused/test",
    username = "unused",
    password = "unused",
    maximumPoolSize = 1,
    minimumIdle = 1,
    connectionTimeout = 1000,
    poolName = "unused",
    autoCommit = true
  )

  private val appConfig = AppConfig(
    bybitAccount = ByBitConfig(
      key = "test",
      secret = "test",
      limit_order_book = 1,
      interval_oi = "5min",
      limit_open_interest = 1,
      recvWindow = "5000",
      pp_check_freq_sec = 1,
      pp_check_restart = 1,
      check_restart_savebar_freq = 1,
      save_order_book_freq_mins = 1,
      save_oi_freq_mins = 1,
      save_futures_data_freq_mins = 1
    ),
    db = dbConfig,
    telegram = TelegramConfig(
      token = "test",
      webhook_port = 8443,
      webhookUrl = "http://localhost",
      keyStorePassword = "test",
      pubcertpath = "/tmp/pub.pem",
      p12certpath = "/tmp/cert.p12",
      usersRefreshMins = 1
    )
  )

  /**
   * Builds a fresh [[UsersService]] on top of the shared test datasource. Kept as a scoped effect so each test gets its
   * own Ref cache while reusing the single container/DataSource from [[DbTestFixtures]].
   */
  private def withUsers[A](
    f: UsersService => ZIO[DataSource with DatabaseService, Throwable, A]
  ): ZIO[DataSource with DatabaseService, Throwable, A] =
    ZIO.scoped {
      UsersService
        .live
        .build
        .provideSomeLayer[DataSource with DatabaseService with Scope](ZLayer.succeed(appConfig))
        .map(_.get[UsersService])
        .flatMap(f)
    }

  private val cleanup = SqlSupport.cleanup(
    s"delete from data.users where user_id in (${userA.user_id}, ${userB.user_id})"
  )

  def spec: Spec[TestEnvironment, Any] =
    suite("UsersServiceSpec")(
      test("loads existing users from DB on startup") {
        val seed    = SqlSupport.execute(
          s"""insert into data.users(user_id, first_name, last_name, username, is_admin)
             | values (${userB.user_id}, 'Bob', null, 'bob', false)
             | on conflict (user_id) do nothing""".stripMargin
        )
        val program = for {
          _     <- cleanup
          _     <- seed
          users <- withUsers(_.getUsers)
          bob   <- ZIO.succeed(users.find(_.user_id == userB.user_id))
        } yield assertTrue(bob.isDefined, bob.get.first_name == "Bob")
        program.ensuring(cleanup)
      },
      test("findUser returns the cached user by telegram id") {
        val seed    = SqlSupport.execute(
          s"""insert into data.users(user_id, first_name, username)
             | values (${userA.user_id}, 'Alice', 'alice')
             | on conflict (user_id) do nothing""".stripMargin
        )
        val program = for {
          _     <- cleanup
          _     <- seed
          found <- withUsers(_.findUser(userA.user_id))
        } yield assertTrue(found.isDefined, found.get.first_name == "Alice")
        program.ensuring(cleanup)
      },
      test("findUser returns None for an unknown telegram id") {
        val program = for {
          _     <- cleanup
          found <- withUsers(_.findUser(-1L))
        } yield assertTrue(found.isEmpty)
        program.ensuring(cleanup)
      },
      test("activeAdmins contains only active admin users") {
        val seed    = SqlSupport.execute(
          s"""insert into data.users(user_id, first_name, username, is_admin, ts_db_end)
             | values
             |  (${userA.user_id}, 'Alice', 'alice', true, null),
             |  (${userB.user_id}, 'Bob', 'bob', false, null)
             | on conflict (user_id) do nothing""".stripMargin
        )
        val program = for {
          _      <- cleanup
          _      <- seed
          users  <- withUsers(_.getUsers)
          admins <- withUsers(_.activeAdmins)
        } yield assertTrue(
          users.exists(_.user_id == userA.user_id),
          admins.exists(_.user_id == userA.user_id),
          !admins.exists(_.user_id == userB.user_id)
        )
        program.ensuring(cleanup)
      },
      test("refreshUsers picks up users inserted after startup") {
        val laterUser = userA
        val program   = for {
          _      <- cleanup
          result <- withUsers { users =>
            for {
              before <- users.findUser(laterUser.user_id)
              _      <- SqlSupport.execute(
                s"insert into data.users(user_id, first_name) values (${laterUser.user_id}, 'Late')"
              )
              _      <- users.refreshUsers
              after  <- users.findUser(laterUser.user_id)
            } yield assertTrue(before.isEmpty, after.isDefined, after.get.first_name == "Late")
          }
        } yield result
        program.ensuring(cleanup)
      }
    ).provideLayerShared(DbTestFixtures.databaseService) @@ TestAspect.sequential

}
