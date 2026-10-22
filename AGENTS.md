# Project: bb_bot

## Стек
- Scala 2.13.16, ZIO 2.1.22
- PostgreSQL 17, Liquibase (YAML)
- com.bot4s telegram-core 6.0.0
- io.getquill quill-jdbc-zio 4.8.5

## Структура
- common/    — общие классы, модели, утилиты
- db/        — Quill-схемы, репозитории, PostgreSQL
- bybit/     — общие классы для работы с Bybit API
- gather/    — сборщик данных с Bybit в БД, клиент Bybit API
- trade_bot/ — бот, Telegram
- dock/migrations/ — Liquibase-миграции (YAML)

## Точки входа
- gather:    bb_bot_project.gather.main.app.Gather — запуск сборщика данных
- trade_bot: bb_bot_project.trade_bot.main.app.TradeBot — запуск Telegram-бота

## Конвенции
- Максимальнь чистый функциональный код с учётом испольования ZIO
- Liquibase-ченджсеты в YAML, changeset-id: YYYY-MM-DD-N-название
- Новые Liquibase-ченджсеты добавляются в конец master.xml
- Все ZIO-эффекты через ZIO.succeed / ZIO.fail
- Логирование через ZIO Logging
- Имена таблиц в snake_case, классы в CamelCase

## Ограничения 
- Не меняйте мажорные версии библиотек в build.sbt без согласования.
- common не зависит от db: тесты Ref-сервисов, которым нужна БД, живут в db/src/test.

## Код-стиль 
- Форматирование — scalafmt (конфиг в .scalafmt.conf). 
- Формат применяется автоматически при компиляции (maxColumn = 120).
- Комментарии к сложным участкам ZIO-пайплайнов обязательны.

## Скиллы
- При работе с SQL/Postgres (запросы, ревью, оптимизация, EXPLAIN, индексы, миграции):
  используй скиллы postgres-pro, postgresql-optimization, postgresql-code-review.

## Команды
- Компиляция: sbt compile
- Тесты модуля: sbt -batch "db/test"
- Один тест: sbt -batch "trade_bot/testOnly service.UsersServiceSpec"
- Формат: sbt scalafmtAll; Проверка: sbt scalafmtCheckAll

## Архитектурные заметки (db)
- Эффекты репозиториев: ZIO[DataSource, SQLException, ...].
- DatabaseService.layer требует AppConfig (не только DbConfig).
- Quill-контекст: QuillCtx.ctx = new PostgresZioJdbcContext(SnakeCase).

## Тесты
- Интеграционные: testcontainers (postgres:17) + программная накатка Liquibase через liquibase-core.
- Общие фикстуры: db/src/test/scala/testsupport/ (PostgresTestContainer, LiquibaseTestRunner, DbTestFixtures, SqlSupport).
- trade_bot видит тестовые классы db через dependsOn(db % "compile->compile;test->test").

## Миграции и локальная БД
- Мастер-чейнджлог: dock/migrations/master.xml.
- Локальная БД: dock/docker-compose.yml; init создаёт схему data, пользователя bb_user/bb_pwd.
