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
- trade_bot/ — бот, Telegram, точка входа com.bb.bot.Main
- dock/migrations/ — Liquibase-миграции (YAML)

## Точки входа
- gather:    bb_bot_project.gather.main.app.Gather — запуск сборщика данных
- trade_bot: bb_bot_project.trade_bot.main.app.TradeBot — запуск Telegram-бота

## Конвенции
- Максимальнь чистый функциональный код с учётом испольования ZIO
- Liquibase-ченджсеты в YAML, changeset-id: YYYY-MM-DD-N-название
- Все ZIO-эффекты через ZIO.succeed / ZIO.fail
- Логирование через ZIO Logging
- Имена таблиц в snake_case, классы в CamelCase

## Ограничения 
- Не меняйте мажорные версии библиотек в build.sbt без согласования.

## Код-стиль 
- Форматирование — scalafmt (конфиг в .scalafmt.conf). 
- Комментарии к сложным участкам ZIO-пайплайнов обязательны.