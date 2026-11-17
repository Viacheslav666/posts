# 02. ТЗ для агента «Posts Разраб Бэк» (бэкенд на Java 21 / Spring Boot 3)

Прочитать: `00-overview.md`, `01-analyst.md`, `docs/analysis.md`. Код: `backend/` (Gradle multi-module). Контракт: генерируемый `docs/api/openapi.yaml`.

## 1. Принципы
1. **PostgreSQL — единственный источник истины.** RabbitMQ — транспорт, а не хранилище состояния.
2. **At-least-once доставка + идемпотентность** на каждом шаге (никаких дублей постов в соцсетях).
3. **Stateless-сервисы**, горизонтальное масштабирование, graceful shutdown (`server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=30s`, листенеры AMQP дожидаются ack текущих сообщений).
4. **Изоляция отказов по сетям:** отдельные очереди, отдельные Deployment’ы `publisher-<network>`, отдельные CircuitBreaker’ы — проблемы VK не тормозят Telegram.
5. **Лимиты API соцсетей соблюдаются централизованно** (Bucket4j поверх Redis), а не «как получится».
6. **Наблюдаемость по умолчанию:** Micrometer-метрики, трейсы OpenTelemetry (`traceparent` пробрасывается через HTTP и заголовки AMQP-сообщений), структурированные JSON-логи с MDC `trace_id`, `workspace_id`, `post_id`, `publication_id`.
7. **Code-first контракт:** DTO + контроллеры с аннотациями springdoc → `./gradlew :apps:api:generateOpenApiDocs` → `docs/api/openapi.yaml` (коммитится, см. §7). Для разработки фронта параллельно бэк сначала выкладывает контроллеры-заглушки (HTTP 501) с финальными DTO.
8. **Модульные границы проверяются ArchUnit:** `domain` не зависит от Spring Web/AMQP/JPA-деталей адаптеров; `apps/*` зависят от `libs/*`, но не друг от друга; адаптеры соцсетей зависят только от `social-api`, `common`, `ratelimit`.

## 2. Gradle multi-module
- `backend/settings.gradle.kts` включает `build-logic` (includeBuild) и модули `libs:*`, `apps:*`.
- `gradle/libs.versions.toml`: Spring Boot 3.4.x+ (BOM через `platform(...)`), springdoc-openapi 2.x, Resilience4j 2.x (`resilience4j-spring-boot3`), Bucket4j 8.x (`bucket4j-redis`/Lettuce), Flyway 10+, Testcontainers 1.20+, WireMock 3, ArchUnit, ShedLock, AWS SDK v2 (S3), Nimbus JOSE (через spring-security-oauth2-jose), Sentry, ClickHouse JDBC, Bouncy Castle (при необходимости).
- Convention-плагины (`build-logic/src/main/kotlin`):
  - `posts.java-conventions` — Java 21 toolchain, `-parameters`, Error Prone + NullAway, Spotless (palantir-java-format), JUnit Platform, JaCoCo (порог 80 % для `domain`, `content`, `social-*`), reproducible builds.
  - `posts.spring-app` — Spring Boot plugin, **Jib** (`gcr.io/distroless/java21-debian12:nonroot`, JVM-флаги, labels, теги `:<git-sha>`), Actuator, Micrometer Prometheus, OTel.
  - `posts.integration-test` — source set `integrationTest` с Testcontainers (запуск `./gradlew integrationTest`).
- Команды для агентов и CI: `./gradlew build` (unit + сборка), `./gradlew integrationTest`, `./gradlew :apps:api:generateOpenApiDocs`, `./gradlew jibDockerBuild` (локально), `./gradlew jib` (CI → GHCR), `./gradlew spotlessApply`.
- Gradle build cache + configuration cache включены; в CI — `gradle/actions/setup-gradle` с кэшем.

## 3. Сервисы (deployable-приложения в `backend/apps/*`)
| Приложение | Ответственность | Масштабирование |
|---|---|---|
| `api` (posts-api) | REST `/api/v1`, WebSocket (STOMP поверх SockJS/нативного WS) для live-статусов, аутентификация (VK ID, Яндекс ID, email, 2FA), RBAC, вебхуки ЮKassa и соцсетей (только приём → очередь) | HPA (CPU + RPS), min 3 реплики, virtual threads (`spring.threads.virtual.enabled=true`) |
| `scheduler` (posts-scheduler) | Диспетчер due-публикаций (`SKIP LOCKED`), материализация повторов, `scheduled_actions` (удаление/открепление/первый комментарий), reaper зависших задач, outbox-relay, продления подписок, token-refresh, создание партиций — периодические задачи под **ShedLock** | 2–4 реплики; диспетчер конкурентен без лидера, cron-задачи — с ShedLock |
| `publisher` (posts-publisher) | Публикация в одну сеть; сеть задаётся `POSTS_PUBLISHER_NETWORK=vk|tg|ok|max|rutube`, загружается только нужный адаптер (`@ConditionalOnProperty`). **Один образ — отдельный Deployment на сеть**: `publisher-vk`, `publisher-tg`, `publisher-ok`, `publisher-max`, `publisher-rutube` | KEDA по длине своей очереди `publish.<network>`; virtual threads |
| `media` (posts-media) | Пост-обработка загрузок в S3: проверка magic bytes, превью, ресайз, транскодирование видео (FFmpeg-процесс в образе с ffmpeg), ClamAV (P1) | KEDA по очереди `media`, отдельный node pool |
| `ingest` (posts-ingest) | Обработка событий из вебхуков (VK Callback API, Telegram updates, Max) → нормализация → `events.*` (подключение TG-каналов в MVP; инбокс, предложка, антиспам, кросспостинг — Фаза 2) | KEDA |
| `notifications` (posts-notifications) | Email (SMTP-провайдер в РФ), Telegram-бот уведомлений, Web Push (VAPID), рассылка WS-событий через брокер | KEDA |
| `analytics` (posts-analytics, Фаза 2) | Сбор статистики постов/каналов батчами с учётом лимитов → ClickHouse | KEDA/cron |

Общее для всех приложений: Actuator `health/liveness`, `health/readiness` (readiness учитывает БД/RabbitMQ/Redis), `/actuator/prometheus`; конфигурация через env/ConfigMap + секреты из Vault (External Secrets); профили `local`, `test`, `staging`, `prod`.

### Virtual threads — правила использования
- Включить в `api`, `publisher`, `ingest`, `notifications` (блокирующие HTTP-вызовы через `RestClient` на `JdkClientHttpRequestFactory`/`HttpClient`, JDBC).
- AMQP-листенеры: `SimpleRabbitListenerContainerFactory` с `VirtualThreadTaskExecutor`; параллелизм ограничивать **prefetch + семафорами/Bulkhead**, а не размером пула потоков.
- Пул JDBC (HikariCP) остаётся ограниченным (например, 20–40 на под через PgBouncer) — виртуальные потоки не увеличивают число соединений; в hot-path не держать транзакцию во время HTTP-вызова к соцсети.
- Избегать pinning: не использовать `synchronized` вокруг блокирующего I/O (использовать `ReentrantLock`), проверять `-Djdk.tracePinnedThreads=short` на staging и JFR-событие `jdk.VirtualThreadPinned`.
- `ThreadLocal`-контекст (MDC, Security) — через Micrometer Context Propagation.

## 4. Модель данных (PostgreSQL, миграции Flyway) — ядро MVP
Все PK — `uuid` (UUIDv7, сортируемый по времени). Все времена — `timestamptz` в UTC. Мягкое удаление `deleted_at`. Мультиарендность: каждая таблица домена содержит `workspace_id` + индекс; все запросы фильтруются по нему (обязательный guard в репозиториях; опционально Postgres RLS).

```sql
-- Пользователи и доступ
users(id, email citext unique, email_verified_at, password_hash /*argon2id (Spring Security Argon2PasswordEncoder)*/, name, avatar_url,
      timezone text default 'Europe/Moscow', locale, totp_secret_enc, totp_enabled bool,
      created_at, updated_at, deleted_at)
user_identities(id, user_id, provider /*vk|yandex|telegram*/, provider_user_id, profile jsonb,
      unique(provider, provider_user_id))
sessions(id, user_id, refresh_token_hash, user_agent, ip inet, created_at, last_used_at, revoked_at)
workspaces(id, name, owner_user_id, plan_id, billing_status /*trial|active|grace|frozen*/,
      trial_ends_at, current_period_end, created_at)
workspace_members(workspace_id, user_id, role /*owner|admin|editor|author|viewer*/, invited_by, created_at,
      primary key(workspace_id,user_id))
invitations(id, workspace_id, email, role, project_ids uuid[], token_hash, expires_at, accepted_at)
projects(id, workspace_id, name, color, created_at)
project_members(project_id, user_id)

-- Каналы
social_accounts(id, workspace_id, network, external_user_id, display_name,
      access_token_enc bytea, refresh_token_enc bytea, token_expires_at, scopes text[],
      status /*active|reauth_required|revoked*/, last_checked_at)
channels(id, workspace_id, project_id, social_account_id, network /*vk|tg|ok|max|rutube*/,
      external_id text, type /*group|page|channel|chat|profile*/, title, avatar_url, username,
      group_token_enc bytea /*VK community token*/, capabilities jsonb,
      status /*active|reauth_required|no_permissions|disabled*/, status_reason, timezone,
      created_at, unique(workspace_id, network, external_id))
channel_sets(id, workspace_id, name) ; channel_set_items(set_id, channel_id)

-- Посты
posts(id, workspace_id, project_id, author_id, title, base_content jsonb /*text, entities, media_ids*/,
      status /*draft|pending_approval|approved|scheduled|publishing|published|partially_published|failed|paused|archived*/,
      scheduled_at timestamptz, recurrence jsonb /*RRULE-подобно: freq, interval, byweekday, until*/,
      rubric_id, is_ad bool, ord_creative_id, version int /*optimistic locking*/,
      created_at, updated_at, deleted_at)
post_variants(id, post_id, channel_id, network, content jsonb /*переопределения текста/медиа*/,
      options jsonb /*pin, unpin_at, delete_after, mute, close_comments, signed, first_comment, buttons, carousel*/,
      unique(post_id, channel_id))
media_assets(id, workspace_id, uploader_id, kind /*image|video|doc|audio*/, s3_key, mime, size_bytes,
      width, height, duration_ms, sha256, status /*uploading|processing|ready|failed*/, derivatives jsonb, created_at)

-- Публикации (одна запись = одна попытка выпустить вариант в конкретный слот времени)
publications(id, workspace_id, post_id, variant_id, channel_id, network,
      run_at timestamptz,                 -- когда публиковать (для повторов — каждое вхождение отдельная запись)
      idempotency_key text unique,        -- sha256(variant_id || run_at)
      state /*pending|queued|in_progress|succeeded|failed_retryable|failed_permanent|cancelled*/,
      attempts int default 0, next_attempt_at timestamptz, locked_by text, locked_until timestamptz,
      remote_post_id text, remote_url text, error_code text, error_message text,
      published_at timestamptz, created_at)
  PARTITION BY RANGE (run_at);            -- партиции по месяцам, pg_partman; старые → архив/ClickHouse
  INDEX (state, run_at) WHERE state IN ('pending','failed_retryable');
  INDEX (post_id); INDEX (channel_id, run_at);
publication_attempts(id, publication_id, attempt_no, started_at, finished_at, http_status, error_code, response_excerpt jsonb)
scheduled_actions(id, publication_id, kind /*delete|unpin|first_comment|edit*/, run_at, state, ...)  -- также партиционирована

-- Прочее ядро
outbox(id bigserial, aggregate, aggregate_id, event_type, payload jsonb, created_at, published_at)
audit_log(id, workspace_id, actor_id, action, entity, entity_id, diff jsonb, ip, created_at)  -- копия в ClickHouse
notifications(id, user_id, type, payload, read_at, created_at)
idempotency_requests(key, user_id, request_hash, response jsonb, created_at)   -- для API Idempotency-Key, TTL 24 ч

-- Биллинг
plans(id, code, name, price_month, price_year, limits jsonb /*channels, seats, posts_month, ai_credits*/)
subscriptions(id, workspace_id, plan_id, status, period_start, period_end, cancel_at_period_end, provider_payment_method_id)
payments(id, workspace_id, provider /*yookassa|tkassa*/, provider_payment_id unique, amount, currency,
      status, receipt_id, created_at)
ledger_entries(id, workspace_id, amount, kind /*charge|payment|refund|promo|referral*/, ref_id, created_at)  -- двойная запись, баланс = SUM
promo_codes(...), referrals(...), invoices(...)
usage_counters(workspace_id, period, metric /*posts|ai_credits*/, value)
```
Фаза 2+: `approvals`, `comments`, `rubrics`, `content_slots`, `ord_contractors`, `ord_contracts`, `ord_creatives`, `ord_reports`, `inbox_threads`, `inbox_messages`, `automation_rules` (автопостинг предложки, антиспам, кросспостинг, RSS, граббер — общая таблица правил с `type` и `config jsonb` + журналы), `api_keys`, `webhook_endpoints`, `webhook_deliveries`.

Миграции: `backend/libs/persistence/src/main/resources/db/migration/V{n}__{описание}.sql`; Hibernate `ddl-auto=validate`; партиции `publications`/`scheduled_actions` создаёт pg_partman (или ShedLock-задача scheduler’а на 3 месяца вперёд).

**Объёмы:** publications ~5 млн/сутки → ~150 млн/мес → помесячные партиции, горячие 3 мес в PG, затем выгрузка в ClickHouse и `DROP PARTITION`. Остальные таблицы — десятки миллионов строк, нормально для PG при правильных индексах.

## 5. Публикационный конвейер (ключевая часть)
### 5.1. Создание
`POST /api/v1/posts` (или `/posts/{id}/schedule`) в одной транзакции (`@Transactional`): пишет `posts`, `post_variants`, материализует `publications` на горизонт **14 дней** для повторов (дальше — ежечасная ShedLock-задача), пишет `outbox` событие `post.scheduled`. Изменение/пауза → `pending` publications → `cancelled` + новые, оптимистичная блокировка `@Version` на `posts.version` (конфликт → 409).

### 5.2. Диспетчеризация (posts-scheduler)
Каждую секунду каждый экземпляр выполняет через `JdbcClient` (не JPA):
```sql
WITH due AS (
  SELECT id FROM publications
  WHERE state IN ('pending','failed_retryable')
    AND coalesce(next_attempt_at, run_at) <= now() + interval '5 seconds'
  ORDER BY coalesce(next_attempt_at, run_at)
  LIMIT 500
  FOR UPDATE SKIP LOCKED)
UPDATE publications p SET state='queued', locked_until = now() + interval '10 minutes'
FROM due WHERE p.id = due.id RETURNING p.id, p.network, p.channel_id, p.run_at;
```
После коммита — отправка в RabbitMQ (`RabbitTemplate` с **publisher confirms** `correlated` + `mandatory`), exchange `publish` (direct), routing key `<network>` → очередь `publish.<network>` (quorum, `x-delivery-limit`, DLX `publish.dlx` → `publish.<network>.dlq`). Сообщение — только `publicationId` + `traceparent`. Не подтверждённые брокером → запись возвращается в `pending`.
**Reaper** (ShedLock, каждые 30 с): `queued/in_progress` с `locked_until < now()` → `failed_retryable`.
Повторная попытка с задержкой — через `next_attempt_at` в БД (диспетчер подберёт), а не через долгие TTL-очереди: состояние всегда в PG.
Пики: по умолчанию точное время; опциональный джиттер ±30 с — только по согласию пользователя.

### 5.3. Публикация (posts-publisher)
`@RabbitListener(queues = "publish.${posts.publisher.network}")`, manual ack, prefetch 50, обработка на виртуальном потоке:
1. Транзакция: `UPDATE publications SET state='in_progress', attempts=attempts+1, locked_by=:pod, locked_until=now()+interval '5 minutes' WHERE id=:id AND state='queued' RETURNING *` — 0 строк → ack (дубль доставки).
2. Если `remote_post_id` уже заполнен → `succeeded`. Если прошлая попытка завершилась с неизвестным исходом (таймаут) → `adapter.findExisting(idempotencyKey)` (VK — параметр `guid` у `wall.post`, защищающий от повторной отправки; TG — сверка последних сообщений канала по хэшу контента/маркеру; OK/Max — по хэшу контента и времени ±2 мин).
3. Получить токен из распределённого лимитера (§5.4). Нет токена → вернуть запись в `failed_retryable` с `next_attempt_at = now() + waitTime`, ack (не блокировать поток и не держать сообщение).
4. Загрузить медиа в сеть (кэш загрузок по `sha256 + channel_id` в Redis), вызвать API через адаптер, обёрнутый Resilience4j: `TimeLimiter` (10–30 с по типу вызова), `CircuitBreaker` на сеть+метод (при открытом CB — публикации сети уходят в `failed_retryable` с задержкой, алерт), `Bulkhead` на канал/токен. **Короткий Retry Resilience4j — только для идемпотентных GET и загрузок медиа**; повтор самой публикации — только через БД (шаг 7), чтобы не потерять контроль над дублями.
5. Транзакция успеха: `succeeded`, `remote_post_id`, `remote_url`, `published_at`; `scheduled_actions` (delete/unpin/first_comment); `outbox` `publication.succeeded` (→ WS, уведомления, аналитика). Затем ack.
6. HTTP-вызов к соцсети **вне** транзакции БД (между шагами 1 и 5 соединение JDBC не удерживается).
7. Ошибка → `adapter.classifyError()`: `RETRYABLE` (5xx, таймаут, VK error 6/9/10, TG 429 с `retry_after`, сетевые) → `failed_retryable`, `next_attempt_at = now() + backoff(attempt)` (5 с, 15 с, 45 с, 2 мин, 5 мин, 10 мин, 15 мин; не более 8 попыток или 60 мин после `run_at`), не раньше `retry_after`; `RATE_LIMITED` → замедление ключа лимитера; `AUTH`/`PERMANENT` (нет прав, токен отозван, контент отклонён) → `failed_permanent`, канал → `reauth_required/no_permissions`, уведомление. Ack. Необработанное исключение → nack без requeue → DLQ + алерт.
8. Агрегированный статус поста (`published / partially_published / failed`) пересчитывается доменным сервисом после каждой публикации (обработчик outbox-события).

### 5.4. Rate limiting (Bucket4j + Redis, Resilience4j как защита)
- **Bucket4j** с `LettuceBasedProxyManager` (Redis) — распределённые token bucket’ы по ключам: `rl:{network}:global`, `rl:{network}:token:{socialAccountId}`, `rl:{network}:chat:{externalId}`; проверка всех уровней (`tryConsumeAndReturnRemaining`), время ожидания возвращается в шаг 3.
- Значения лимитов — в конфигурации (`posts.ratelimit.<network>.*`, обновляемы без релиза через ConfigMap/feature flags); Аналитик уточняет по `network-capabilities.md`. Стартовые:
  - VK: ≤ 3 запроса/с на пользовательский токен, ≤ 20/с на токен сообщества; `execute` для батчей чтения; error 9 (flood control) → блокировка ключа на 1 ч; учитывать суточные лимиты на публикацию.
  - Telegram Bot API: ≈ 30 сообщений/с на бота глобально, ≈ 20 сообщений/мин в одну группу/канал; **пул ботов** (канал закрепляется за ботом при подключении) для пика > 30/с.
  - OK, Max, Rutube — по документации; по умолчанию консервативно 5 запросов/с на токен.
- Адаптивность (AIMD): при 429/flood control ёмкость ключа временно снижается, метрика `posts_ratelimit_throttled_total{network,level}`.
- **Деградация при недоступности Redis:** fallback на локальный Resilience4j `RateLimiter` с лимитом `global / число реплик` (консервативно), алерт; шторма запросов к соцсетям не допускается.

### 5.5. Повторы, автоудаление, открепление, первый комментарий
Тот же механизм: `scheduled_actions` → очередь `actions.<network>` → тот же `publisher-<network>` (отдельный `@RabbitListener`), идемпотентно (удаление уже удалённого = успех).

### 5.6. Нативное отложенное VK
Не в MVP: публикуем сами (единая логика ретраев и статусов). Опция «страховки» нативной отложкой — ADR в Фазе 2.

## 6. Адаптеры соцсетей (`libs/social-api` + `libs/social-<network>`)
```java
public interface SocialAdapter {
    Network network();
    NetworkCapabilities capabilities();                       // лимиты, форматы, поддерживаемые опции
    List<ValidationIssue> validate(PostVariant variant);      // вызывается при сохранении поста
    RemoteMediaRef uploadMedia(ChannelContext ctx, MediaAsset asset);
    PublishResult publish(ChannelContext ctx, PostVariant variant, String idempotencyKey);
    Optional<PublishResult> findExisting(ChannelContext ctx, String idempotencyKey, PostVariant variant);
    void delete(ChannelContext ctx, String remotePostId);
    void pin(ChannelContext ctx, String remotePostId, boolean pinned);
    void comment(ChannelContext ctx, String remotePostId, String text);
    List<PostStats> fetchStats(ChannelContext ctx, List<String> remotePostIds);
    ErrorClass classifyError(Throwable error);                // RETRYABLE | RATE_LIMITED | AUTH | PERMANENT
    default Optional<TokenSet> refreshToken(SocialAccount account) { return Optional.empty(); }
}
```
- HTTP — Spring `RestClient` (таймауты connect 3 с / read 10–30 с), JSON — Jackson; секреты/токены расшифровываются только в памяти на время вызова, в логах — маскирование.
- Реализации MVP: **VK** (VK ID OAuth 2.1 + PKCE; `wall.post` с `guid`; загрузка фото/видео/документов), **Telegram** (Bot API: `sendMessage/sendPhoto/sendMediaGroup/sendVideo/sendDocument`, `parse_mode=HTML`, `pinChatMessage`, `deleteMessage`, inline-кнопки; вебхук с `secret_token`), **OK** (REST API, `mediatopic.post`), **Max** (Bot API Max: публикация в каналы, форматирование, вебхуки). Фаза 2: Rutube, истории VK/OK.
- Рендеринг контента — `libs/content`: `ContentRenderer` на сеть (Tiptap-JSON/внутренняя модель → TG HTML, Max HTML, plain text VK/OK), лимиты сетей; **эталонные фикстуры** `docs/api/fixtures/formatting/*.json` (вход → ожидаемый выход по каждой сети) используются и в JUnit-тестах бэка, и в Vitest-тестах предпросмотра фронта — серверный рендер является авторитетным.
- Тесты адаптеров: WireMock-стабы с записанными ответами (включая ошибки и таймауты) + ежедневный smoke против реальных тестовых каналов на staging.

## 7. API (`/api/v1`, springdoc-openapi)
- Auth: `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/oauth/{provider}/start|callback`, `/auth/2fa/*`, `/auth/password/forgot|reset`, `GET /me`, `GET /me/sessions`.
- Workspaces/команда: `/workspaces`, `/workspaces/{id}/members`, `/invitations`, `/projects`.
- Каналы: `/channels`, `/channels/connect/{network}/start|callback`, `/channels/{id}/check`, `/channel-sets`, `GET /networks/capabilities` (лимиты и опции сетей для валидации на фронте).
- Посты: `/posts` (фильтры `from,to,status,channelId,projectId,network,q`, курсорная пагинация), `/posts/{id}`, `/posts/{id}/schedule|pause|resume|duplicate`, `/posts/bulk`, `/posts/{id}/publications`, `/calendar?from&to` (облегчённые DTO), `POST /posts/preview` (серверный рендер варианта для сети).
- Медиа: `POST /media/uploads` → presigned multipart URL S3; `POST /media/{id}/complete`; `GET /media`.
- Биллинг: `/billing/plans`, `/billing/subscription`, `/billing/checkout`, `/billing/invoices`, `/billing/promo/apply`, `POST /webhooks/yookassa` (проверка IP-диапазонов/подписи, идемпотентно по `provider_payment_id`).
- Уведомления: `/notifications`, `/notification-settings`.
- Вебхуки соцсетей: `/hooks/vk/{channelId}` (Callback API confirmation + secret), `/hooks/tg/{botId}` (`X-Telegram-Bot-Api-Secret-Token`), `/hooks/max/{botId}` — только валидация и публикация в очередь `ingest` (ответ < 100 мс).
- WebSocket: `/ws` (STOMP), топики `/topic/workspaces/{id}/publications`, `/user/queue/notifications`; авторизация JWT при CONNECT; межподовая рассылка через RabbitMQ STOMP relay или fan-out exchange.
- Правила: `Idempotency-Key` для POST, создающих сущности/платежи (фильтр + таблица `idempotency_requests`); ошибки **RFC 7807** (`ProblemDetail` Spring 6) с полем `code`; оптимистичная блокировка через `version`/ETag; лимиты API пользователя 20 RPS / 1 000 в мин на токен (Bucket4j); все DTO — Java `record` с Bean Validation; аннотации `@Schema`/`@Operation` обязательны (CI проверяет, что у операций есть `operationId` и описание).
- **Генерация контракта:** Gradle-задача `:apps:api:generateOpenApiDocs` — `@SpringBootTest` (`OpenApiExportTest`, Testcontainers Postgres/Redis/RabbitMQ через `@ServiceConnection`) запрашивает `/v3/api-docs.yaml` и пишет `docs/api/openapi.yaml` (альтернатива — `org.springdoc.openapi-gradle-plugin` с профилем `openapi`); CI падает, если сгенерированный файл отличается от закоммиченного; `oasdiff` детектирует breaking changes → требуется метка `contract-change`. Фронт генерирует TS-клиент из этого файла (`03-frontend.md`).

## 8. Аутентификация и безопасность (Spring Security 6)
- Пароли: `Argon2PasswordEncoder`; access JWT (ES256, 15 мин) выпускается нашим сервисом (Nimbus через `spring-security-oauth2-jose`), ключи — из Vault, публикация JWKS `/.well-known/jwks.json`; API — **Resource Server** (`oauth2ResourceServer().jwt()`); refresh — opaque 30 дней, хэш в `sessions`, ротация и детект reuse (повтор → отзыв семейства); для веба refresh в httpOnly Secure SameSite=Lax cookie.
- Вход через VK ID, Яндекс ID, OK — **Spring Security OAuth2 Client** (кастомные `ClientRegistration`, PKCE, state/nonce); Telegram Login — проверка hash; после успешного OAuth выдаём свои токены.
- RBAC: `@PreAuthorize` + собственный `PermissionEvaluator` по матрице `01-analyst.md`; workspace/проект проверяются на каждом запросе (Hibernate filter по `workspace_id` + проверка в сервисах); тесты на IDOR обязательны.
- 2FA TOTP (библиотека `dev.samstevens.totp` или аналог), резервные коды (хэши).
- **Шифрование токенов соцсетей:** envelope encryption — DEK на workspace (AES-256-GCM, JCA), DEK шифруется KEK из KMS (Yandex KMS / Vault Transit); JPA `AttributeConverter` не используется для токенов (явный сервис расшифровки, чтобы токены не попадали в кэши/логи).
- Защита: Bucket4j на логин (5/мин/IP+email), Yandex SmartCaptcha после 3 неудач, CSRF для cookie-эндпоинтов (`CookieCsrfTokenRepository`), CORS whitelist, заголовки безопасности, проверка загрузок по magic bytes (Apache Tika), SSRF-защита для URL (RSS, картинки по ссылке — запрет приватных диапазонов, DNS-rebinding), секреты только из Vault/K8s secrets, маскирование в логах (Logback-маскер).
- 152-ФЗ: данные в РФ, согласие на обработку ПДн, экспорт/удаление данных (`DELETE /me` → анонимизация через 30 дней).

## 9. Биллинг
- Подписка на workspace; лимиты (`plans.limits`) проверяет доменный `PlanLimitService` (каналы, места, посты/мес, AI-кредиты) → 402 `plan_limit_exceeded`.
- ЮKassa: создание платежа с `save_payment_method`, вебхуки `payment.succeeded/canceled`, `refund.succeeded`, чеки 54-ФЗ, идемпотентность по `Idempotence-Key`.
- Ежедневная ShedLock-задача: продления (D-1, D0, D+2), grace 3 дня → `frozen` (публикации → `paused` с причиной `billing`), разморозка после оплаты (просроченные — на выбор пользователя).
- Ledger двойной записи (`ledger_entries`), суммы — `BigDecimal`/копейки `long`; режим «посуточное списание» (Фаза 2) — на том же ledger.

## 10. Фаза 2 — ключевые модули (проектировать расширяемо)
- **ORD:** интерфейс `OrdProvider` (VK ОРД, ОРД Яндекса): контрагенты, договоры, креативы → erid → вставка «Реклама. ИНН. erid: …» в вариант; ежемесячные отчёты; справочники ККТУ/ОКСМ с синхронизацией.
- **Ingest/автоматизации:** события → `SocialEvent` (sealed interface: NewComment, NewMessage, SuggestedPost, WallPostNew, MemberJoin…) → topic exchange `events` → потребители (инбокс, предложка, антиспам, кросспостинг, боты); правила `automation_rules` (JSONB-конфиг, версия).
- **Аналитика:** сбор статистики через 1 ч / 24 ч / 72 ч / 7 дней после публикации + ежедневная по каналам → ClickHouse (`post_stats`, `channel_stats_daily`, materialized views); API аналитики читает только ClickHouse.
- **AI:** интерфейс `LlmProvider` (YandexGPT, GigaChat, OpenAI-совместимые; можно Spring AI), промпт-шаблоны в БД, учёт кредитов, TimeLimiter + fallback провайдера.
- **Публичное API и вебхуки:** API-ключи (хэш, scope), исходящие вебхуки с HMAC-подписью, ретраи 24 ч, журнал доставок.

## 11. Производительность и ёмкость (100k пользователей, запас ×3)
| Компонент | Оценка | Решение |
|---|---|---|
| posts-api | 10k онлайн × ~0.5 RPS = 5k RPS пик | 6–10 подов (2 vCPU / 2–3 ГБ heap), virtual threads, HikariCP 30 → PgBouncer (transaction mode), Redis-кэш `/me`, планов, каналов |
| posts-scheduler | 2 000 publications/с пик | батчи по 500, 2–4 экземпляра, индекс (state, run_at) по горячей партиции, отправка в RabbitMQ пакетами с confirms |
| posts-publisher | 2 000/с × ~400 мс = ~800 одновременных вызовов | 10–20 подов суммарно по сетям (на под 50–100 одновременных вызовов на виртуальных потоках, ограничено Bulkhead), KEDA по очередям |
| PostgreSQL | ~5–8k TPS пик | 16 vCPU / 64 ГБ primary, NVMe, 2 реплики (чтение календаря/списков — с реплик через отдельный `DataSource` и `@Transactional(readOnly = true)` маршрутизацию, допустимый лаг ≤ 1 с), партиционирование |
| RabbitMQ | 2–5k msg/с | 3 узла, quorum queues |
| Redis | лимиты ~10k ops/с | Sentinel 3 узла / managed |
| JVM | старт пода | AppCDS/Spring AOT для старта < 10 с (важно для KEDA), pre-scaling перед ровными часами |
Нагрузочный профиль — совместно с Тестировщиком (`05-qa.md`).

## 12. Задачи по порядку (MVP)
1. (Нед. 1–2) ADR-001…006: стек Java/Spring, модульная структура, мультиарендность, публикационный конвейер, RabbitMQ-топология, шифрование токенов; JPA vs jOOQ для hot-path.
2. (Нед. 2) Flyway-схема ядра + JPA-сущности + контроллеры-заглушки и DTO ядра → `docs/api/openapi.yaml` → ревью Фронт/QA (гейт C).
3. (Спринт 1) Auth (email, VK ID, Яндекс ID, 2FA, сессии, JWT/JWKS).
4. (Спринт 1–2) Workspaces/проекты/члены/приглашения + RBAC + аудит.
5. (Спринт 1) **Прототип конвейера** scheduler → RabbitMQ → publisher на эмуляторе соцсетей (главный риск).
6. (Спринт 2–3) Каналы: VK, Telegram (бот), OK, Max; проверка статуса, token-refresh.
7. (Спринт 2) Медиа (S3 multipart, posts-media).
8. (Спринт 3) Посты/варианты/рендеринг/валидация/календарь API.
9. (Спринт 3–4) Конвейер в прод-качестве: лимиты Bucket4j, Resilience4j, идемпотентность, `scheduled_actions`, DLQ, метрики.
10. (Спринт 4) Уведомления (email, TG-бот, WS, Web Push).
11. (Спринт 4–5) Биллинг ЮKassa + лимиты + промокоды.
12. (Спринт 5) Админ-API; харденинг: нагрузочные и хаос-тесты, профилирование (JFR, pg_stat_statements).

## 13. Definition of Done для бэка
JUnit 5-тесты домена (JaCoCo ≥ 80 % для `domain`, `content`, `social-*`); интеграционные тесты на Testcontainers (Postgres, Redis, RabbitMQ, MinIO); WireMock-тесты адаптеров; ArchUnit-правила зелёные; `openapi.yaml` перегенерирован; миграции expand/contract без блокирующих ALTER на больших таблицах (проверка squawk); метрики, трейсы и runbook для новых алертов; Spotless/Error Prone без нарушений.
