# План тестов bb_bot

Обзор покрытия текущими тестами и план, что добавить. Все тесты — zio-test (`ZIOSpecDefault`, `assertTrue`, `TestAspect.sequential`), интеграционные — через существующие фикстуры `db/src/test/scala/testsupport/` (`DbTestFixtures`, `SqlSupport`). По конвенции AGENTS.md: Ref-сервисы, которым нужна БД, тестируются в `db/src/test`; тесты, использующие только тестовые классы db из trade_bot, — через `dependsOn(db % "test->test")`.

## Что уже есть

| Спека | Модуль | Что покрыто |
|---|---|---|
| `DatabaseServiceSpec` | db | isAvailiable, словари (symbols/coins/log levels/refs), users upsert/get, логи, advice-интервалы, tg_command_log |
| `DictionaryServicesSpec` | db | Ref-сервисы `SymbolsService`, `CoinService` (гидратация, поиск, ошибки) |
| `UsersServiceSpec` | trade_bot | Ref-кэш пользователей, findUser/Admins/refresh |

Покрытие в основном закрывает **чтение словарей и пользователей**. Белые пятна: модули `bybit`, `gather`, `common/conf`, большинство сервисов `trade_bot`, чистая бизнес-логика `AdvisorService`.

---

## Приоритет 1 — чистые unit-тесты (без БД, быстрые)

### 1.1 `common`: `IntervalSpec` → `common/src/test/scala/bybit_model/IntervalSpec.scala`
- Round-trip `IntervalCode <-> минуты` для всех интервалов из Liquibase-справочника (1, 3, 5, 15, 30, 60…).
- **Требует**: создать `common/src/test`; проверить, что в build.sbt у common подключены test-зависимости (zio-test), иначе добавить.

### 1.2 `common`: `KLineTopicSpec` → `common/src/test/scala/bybit_model/KLineTopicSpec.scala`
- `KLineTopic("kline.15.BTCUSDT")` → парсинг интервала и символа.
- Обратная сериализация: `KLineTopic(symbol, interval).getTopicString == исходная строка`.
- Некорректные входы (пустая строка, лишние сегменты, не-число в интервале) → корректная ошибка / `Left`.

### 1.3 `bybit`: `AuthMessageEncoderSpec` → `bybit/src/test/scala/bybit/AuthMessageEncoderSpec.scala`
- `authMessageEncoder.encodeJson` даёт ожидаемый JSON (порядок полей `req_id`, `op`, `args`, экранирование спецсимволов в элементах массива) — `args` это `Array[String]`, деривация энкодера не очевидна для массивов.
- **Требует**: создать `bybit/src/test`. Зависимости `zio-json` уже в bybit.

### 1.4 `bybit`: `ApiDecodeSpec` → `bybit/src/test/scala/bybit/ApiDecodeSpec.scala`
- Правый путь: `Right(Right(a))` → `a`.
- Левые: `Left(ex: Throwable)` → fail с тем же exception.
- `Left(new Error("not an Exception"))` (т.е. `Throwable`, не `Exception`) → оборачивается в `new Exception(t)` — это конкретная ветка match в `ApiDecode.scala:10-11`.
- Вложенный `Left(msg)` → fail `new Exception(msg)` с текстом msg.

### 1.5 `trade_bot`: `AskQueueServiceSpec` → `trade_bot/src/test/scala/service/AskQueueServiceSpec.scala`
- `Queue.unbounded`: offer/take по порядку, shutoff → `take` завершается корректно.

### 1.6 `bybit`: `PingPongJsonSpec` → `bybit/src/test/scala/bybit/PingPongJsonSpec.scala`
- `Ping().toJson` == ожидаемый литерал (проверить `"op":"ping"` и т.п.).
- Декодер `Pong`: валидный JSON (`op":pong`, `success/ret_msg/conn_id/ts`) декодируется; мусор → `Left`.
- Декодер `SubscribeCoins` / `SuccessSubscribeKLine`: round-trip.

### 1.7 `bybit`: `SignSpec` → `bybit/src/test/scala/bybit/SignSpec.scala`
- Извлечь хелпер подписи (или тестировать через反射/публичную обёртку): HMAC-SHA256 от `timestamp+key+recvWindow+query`, hex-кодирование в нижнем регистре.
- На валидных данных сверка с известным HMAC (независимая реализация в тесте).
- **Опционально/позже** — требует небольшого рефакторинга (сейчас `sign` — `private` в `ByBitServiceImpl`).

---

## Приоритет 2 — сервисы Ref/Ref-state (common)

### 2.1 `common`: `PingPongServiceSpec` → `common/src/test/scala/services/PingPongServiceSpec.scala`
Ref-сервис без БД → можно в `common/src/test` (не противоречит правилу AGENTS.md — БД не нужна).
- `initEmptyPingPong` добавляет запись с текущим `emptyOrLastPingSaved`.
- `savePing` ставит ping/cбрасывает pong=0 и обнуляет `emptyOrLastPingSaved`; создаёт запись, если её не было (ветка `case None` в `savePing`).
- `savePong` — обновляет только если запись уже есть (`case None => None`).
- `saveState` идемпотентен (не меняет при том же state) и меняет при новом.
- `saveNeedRestart` обнуляет ts, fiber=None, needRestart=true; для несуществующего ключа — no-op.
- `check` с тестовыми часами (`TestClock`): порог `ppCheckInterval` превышен → state=Timeout + fiber прерван, `needRestart=true`; второй случай `Timeout` (`diff <= 8` и `getLastPingSecBack >= 30`); ровно на границе — ничего.
- `checkAllIntervals` обходит все интервалы.
- `getIntervalsForRestart` возвращает только `needRestart`, `getIntervalForRestart` — первый из них.
- `remove`/`interruptFiberRemove` (fiber действительно прерывается — проверить по fiber.isAlive), `getKeysIntervals`.
- Тесты с `TestClock`: установить время, дёрнуть `check`, сдвинуть `TestClock.adjust(...)`.
- Потенциальные баги для ловли: `saveState` идемпотентен только по `state != new`, но при первом `check` state=Ok и не меняется до второго условия.

### 2.2 `common`: `LogLevelServiceSpec` → `common/src/test/scala/services/LogLevelServiceSpec.scala`
- `add` увеличивает `getCount`; `findByCode` — hit и `NotFoundInLogLevel` (зеркально `DictionaryServicesSpec`, но без БД).

### 2.3 `trade_bot`: `AdvisorServiceSpec` (unit) → `trade_bot/src/test/scala/service/AdvisorServiceFoldSpec.scala`
- `foldSymbolsAdviceProc` сейчас `private` — **предложить рефакторинг**: вынести в companion `object AdvisorService` (или `private[service]`). Тогда чистый unit-тест: List[SymbolAdviceProc] → List[SymbolsAdviceProc] — группировка по (advisorId, func, mins), множество символов, сортировка; пустой список → пустой; дубликаты склеиваются.
- `runForInterval` — интеграционно: мок `TelegramService` + testcontainer: seed advice_meta/symbols_intervals, вызов, проверка вызова `getAndSaveAdvice` и отправки (TelegramService мок через `MockService` или тестовый слой).

### 2.4 `trade_bot`: `CommunicationServiceSpec` (unit) → `trade_bot/src/test/scala/service/CommunicationServiceSpec.scala`
- Чистые функции `calcBuyLimitPrice`/`calcBuyMarketPrice`/`calcSellPrice`/`calcQuantity` (если public; иначе через `makeBuyOrderAsk` с мок-советом, `TestRandom` засидить через seed).
- Форматирование сумм (`formatQty`, `truncate`) — округление вниз, случай с нулём и мелкими суммами.
- Парсинг `getViewDeep` из строки `"/getViewDeep 15 10"` → валидный Ask, `"/getViewDeep abc"` → `GetViewDeepInvalid`. Если парсер в `TelegramCommands` — то там и тестировать.
- Зависит от доступности `UsersService` — мок-слой: `ZLayer.succeed` с in-memory stub или фикстура users.

---

## Приоритет 3 — интеграционные (testcontainer, в db/src/test)

### 3.1 `db`: `PostgresqlWriteSpec` — дописать в существующий `DatabaseServiceSpec` или новый
- `saveAdviceInsert` / `getAllAdvice` — вставка advice, выборка только неотправленных (или какой контракт).
- `getAndSaveAdvice` (используется in `AdvisorService`) — идемпотентность повторного вызова, возврат id новых advice.
- `saveOrderBook` / `saveOpenInterest` / `saveBars(saveCandles)` — вставка пачки, проверка записей/дедупликация.
- `saveWalletBalances` — `getMaxWalletBalanceId` + вставка.
- `getDeepLines` (ViewDeepLine, нужен для `/getViewDeep`).
- `saveFuturesData`, `reglamentInsert`, `saveReglamentLogs`, `saveErrorInDb`.
- Тесты ошибок FK/уникальности (например, advice на несуществующий символ → SQLException).

### 3.2 `db`: `ReglamentServiceSpec` → `db/src/test/scala/db/ReglamentServiceSpec.scala`
- Гидратация Ref-словаря регламентов из БД (по образцу `DictionaryServicesSpec`): add/get/refresh, `RefReglamentMetaInterval`-логика, если есть.
- Сейчас `ReglamentService` лежит в `trade_bot`, но по конвенции AGENTS.md Ref-сервис с БД тестируется в db — либо перенести сервис в common ( скоуп рефакторинга, не сейчас), либо тест в db через `dependsOn`.

### 3.3 `db`: `DictChangesSpec` → `db/src/test/scala/db/DictChangesSpec.scala`
- На testcontainer: `NOTIFY bb_dict_changed, 'symbols'` → подписчик получает `TableChanged("symbols")` (через `ZStream.runHead` с `TestClock`/timeout).
- События триггеров Liquibase: `UPDATE data.symbols SET ...` → уведомление приходит автоматически (проверка миграции `dict_notify.xml`).
- `Resync` при (пере)подключении: оборвать соединение → после реконнекта первым идёт `Resync`.
- `TestClock` для поллинга 200ms; пометить `@@ TestAspect.withLiveClock` там, где LISTEN реально по-сетевому.

---

## Приоритет 4 — HTTP-клиент Bybit (заглушка сервера)

### 4.1 `bybit`: `ByBitHttpEndpointsSpec` → `bybit/src/test/scala/bybit/ByBitHttpEndpointsSpec.scala`
- Использовать `zio-http` тестовый сервер: `TestClient` / `serve` с `Response.json(...)` фикстурами.
- `getOrderBook` / `getOpenInterest` / `getFuturesData` / `getWalletBalance` / `orderCreate` / `getOrderHistInfo`:
  - успешный ответ → правильный `result` + проверять заголовок `X-BAPI-SIGN` не пустой, `X-BAPI-API-KEY` == key из конфига;
  - битый JSON → ошибка с понятным текстом (ветка `ApiDecode.unwrap`);
  - `{ "retCode": 0, "retMsg": "...", "result": ... }` vs `retCode != 0` — проверить, как декодер это интерпретирует (если реткоды не мапятся на ошибку — найти баг).
- `orderCreate` с `LimitTradeAdvice` и `MarketTradeAdvice` — тело запроса сериализовано корректно ( captured body на mock-сервере).

### 4.2 `bybit`: `ByBitWebSocketRoutingSpec` (опционально, сложно)
- Роутинг `parseResponseJson` сейчас `private` — если вынести в `private[bybit]` helper: строка с `"op":"subscribe"` → логирует subscribe; `"topic":"kline."` → вызывает handler (мок KLineHandler и SymbolService); `"ret_msg":"pong"` → `savePong`; мусор → игнор. Без реального WebSocket.

---

## Приоритет 5 — Telegram / конфигурация (лучше поздних, после рефакторингов)

### 5.1 `common`: `ConfigSpec` → `common/src/test/scala/conf/ConfigSpec.scala`
- `ConfigLoad.load` с тестовым JSON/TOML (положить `src/test/resources/test.conf`): парсинг `AppConfig` (bybit/db/telegram), отсутствие поля → внятная ошибка.
- `DbConfig.asHikariConfig` — маппинг полей + `SET TIME ZONE 'UTC'` init SQL.
- Требуется: понять источник конфигурации (typesafe config / zio-config — по `ConfigLayer.scala`).

### 5.2 `trade_bot`: `TelegramCommandsParseSpec` (unit, если парсер отделён от bot4s)
- Парсинг текстовых команд в `Ask` (.getViewDeep с аргументами, /help, /getCommonBalance…) — требует выделения парсера в чистую функцию из `TelegramCommands` (сейчас завязан на bot4s `Message`).

### 5.3 `trade_bot`: `TraderServiceSpec` — интеграция
- `try2makeOrder` → вызов `ByBitService.orderCreate` (мок ByBitService: успешный/ошибочный респонс), логирование в reglament_log через `DatabaseService`, `checkStatus` → `getOrderHistInfo` до `isFilled`.
- Мок ByBitService через `MockService` zio-test или in-memory stub слой.

---

## Рефакторинги, разблокирующие тесты (обсудить отдельно)
1. `AdvisorService.foldSymbolsAdviceProc` — сделать доступной для теста (private → companion object).
2. Парсер команд Telegram / ценовые калькуляции `CommunicationService` — вынести чистые функции.
3. Роутинг `parseResponseJson` ByBitService — вынести в `private[bybit]` или отдельный объект.
4. Подпись `sign()` ByBitService — сделать тестируемой отдельно от HTTP.

## Порядок работ
1. Приоритет 1 (всё чистое, без БД и рефакторингов; завести test-каталоги для common и bybit — проверить test-настройки в build.sbt).
2. Приоритет 2.1–2.2 (PingPong/LogLevel — чистые Ref-сервисы).
3. Приоритет 3 (интеграции сtestcontainer, расширение DatabaseServiceSpec).
4. Дальше по приоритетам.
