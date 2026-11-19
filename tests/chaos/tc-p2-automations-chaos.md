# Chaos-сценарии P2-автоматизаций (QA-14 / BE-32…BE-36)

Документ описывает **обязательные** хаос-кейсы для фазы 2 (inbound, antispam, crosspost). Локальный полный хаос — compose profile **`full`**; в CI — **`@Tag("nightly")`** IT на Testcontainers.

Связанные TC: `docs/qa/test-cases/tc-inbound.md` (**TC-IN-08**), traceability `docs/qa/traceability-tc-inbound.md`, `docs/qa/traceability-tc-antispam.md` (Redis).

---

## TC-P2-CH-01 — RabbitMQ stop + restart `posts-ingest` (поток inbound)

| Поле | Значение |
|---|---|
| **ID** | TC-P2-CH-01 / TC-IN-08 |
| **Приоритет** | P0 |
| **Автотест** | `backend/apps/api/src/integrationTest/java/ru/posts/api/inbound/Qa14InboundChaosIT` |
| **Ручной прототип** | stop `rabbitmq` в compose 2–5 мин во время инъекции `/_control/vk/callback` |
| **Критерий** | **0 потерь**, **0 дублей** после восстановления |

### Шаги (ручной / staging)

1. Поднять `make up-full` (api, ingest, rabbitmq, emulator).
2. Запустить непрерывную инъекцию VK callback (эмулятор или k6 `l3-social-inbound-hooks.js` с умеренным rate).
3. `docker stop` контейнер RabbitMQ **или** `rabbitmqctl stop_app` на 3 минуты.
4. Наблюдать: хуки `posts-api` → **503** + `Retry-After` (VK/TG/Max ретраят по политике сети).
5. Поднять RabbitMQ; рестарт `posts-ingest` если процесс завис.
6. Дождаться drain очередей `ingest.vk|tg|max`.

### Ожидаемый результат

- Уникальные `event_id` / dedup keys в `social_inbound_events` — **ровно по одному** на событие.
- Тестовый / production handler: счётчик обработок = числу уникальных событий.
- Метрика `social_inbound_hook_lost` (k6) = **0** за окно после восстановления.
- DLQ: нет необработанных poison-сообщений без ручного разбора (или явный alert).

### Автотест `Qa14InboundChaosIT` (контракт)

- Testcontainers: Postgres + RabbitMQ; эмулятор на WireMock.
- Фаза A: публикация N callback с уникальными `event_id`.
- Фаза B: остановка listener / блокировка очереди (simulate broker down) — API возвращает 503.
- Фаза C: возобновление; повторная доставка тех же `event_id` от «сети».
- Assert: N строк в `social_inbound_events`, handler invocation = N.

---

## TC-P2-CH-02 — Redis degrade (лимитер / album buffer / repeat detector)

| Поле | Значение |
|---|---|
| **ID** | TC-P2-CH-02 |
| **Приоритет** | P0 |
| **Автотест** | `backend/libs/test-support/src/test/java/ru/posts/testsupport/chaos/Qa14RedisDegradeTest` (и/или `backend/apps/api/.../Qa14RedisDegradeIT`) |
| **Зоны** | BE-32 TG album 3s; BE-35 `RepeatDetector` / rate limit; BE-33 Bucket4j |
| **Критерий** | **Деградация без потерь** (задержка или fail-safe), события не теряются |

### Шаги

1. Compose profile `full`; убедиться, что Redis Sentinel/standalone healthy.
2. Во время потока antispam/inbound (≥100 событий/с):
   - `docker pause` redis **или** iptables DROP на порт Redis на 60–120 с.
3. Восстановить Redis.

### Ожидаемый результат

- **Inbound:** альбомы TG могут сливаться с задержкой &gt;3 с, но финально **одно** событие с полным набором вложений (JDBC album fallback при недоступном Redis — по реализации).
- **Antispam:** при недоступном Redis repeat-detector — консервативный режим (не удалять «вслепую») **или** пропуск repeat-фильтра с метрикой degrade; события остаются в ingest.
- **Rate limit:** fail-safe — действия **ставятся в очередь** (`social_remote_actions`), не отбрасываются.
- После восстановления Redis: очередь догоняется; **0** потерянных `social_inbound_events`; **0** дублей публикаций.

### Автотест `Qa14RedisDegradeTest`

- Мок `StringRedisTemplate` / Testcontainers Redis stop:
  - вызов `TelegramAlbumAggregator` → assert fallback path;
  - вызов `RepeatDetector.isRepeat` → false safe + metric;
  - `Bucket4j` / limiter → enqueue, not drop.

---

## Матрица chaos ↔ автотест

| Сценарий | IT | Nightly | Staging |
|---|---|---|---|
| RabbitMQ down + ingest restart | `Qa14InboundChaosIT` | да | runbook `social-inbound.md` |
| Redis pause/degrade | `Qa14RedisDegradeTest` | да | опционально |
| Publisher kill (crosspost backlog) | по аналогии `tc-pub-chaos.md` / `TcPubExtendedIT` | P1 | `kill-publisher.sh` |

## Запуск nightly

```bash
cd backend && ./gradlew :apps:api:integrationTest -PincludeNightly=true \
  --tests 'Qa14InboundChaosIT'

cd backend && ./gradlew :libs:test-support:test -PincludeNightly=true \
  --tests 'Qa14RedisDegradeTest'
```

## История

| Дата | Изменение |
|---|---|
| 2026-11-19 | Первичный документ QA-14 (Rabbit + Redis) |