# 00. Обзор проекта «Posts» — общий документ для всех агентов

> Рабочее название продукта: **Posts**. Цель — SMM-платформа, аналогичная vposter.ru и превосходящая её (см. `docs/analysis.md`).
> Репозиторий: **https://github.com/Viacheslav666/posts** (монорепозиторий).
> Исполнители — **облачные агенты Cursor** (Posts Аналитик, Posts Разраб Бэк, Posts Разраб Фронт, Posts Devops, Posts Тестировщик). Каждая задача — отдельная ветка и Pull Request в этом репо. Порядок первых задач — `agent-tasks.md`.

## 1. Видение продукта
Единая платформа для SMM-специалистов, агентств и бизнеса в российском сегменте: планирование и гарантированная публикация контента в VK, Telegram, OK, Max, Rutube (и далее другие сети), командная работа с согласованием, кросс-сетевая аналитика, единый инбокс, AI-студия, маркировка рекламы через ОРД и набор автоматизаций VK-сообществ (паритет с Впостером).

**Ключевые обещания пользователю:**
1. Пост выйдет вовремя (p99 ≤ 60 с от запланированного времени) или вы узнаете об этом мгновенно, а пост не потеряется.
2. Вся команда и клиенты работают в одном месте с ролями и согласованием.
3. Один редактор — все сети, с точным предпросмотром и валидацией.
4. Аналитика и отчёты по всем сетям, а не только VK.

## 2. Нефункциональные требования (обязательны для всех ролей)
| Требование | Целевое значение |
|---|---|
| Пользователи | 100 000 зарегистрированных, проектируем с запасом ×3 (300 000) |
| Одновременные активные (DAU / пик онлайн) | 30 000 DAU / 10 000 одновременно |
| Подключённые каналы | до 1 500 000 (≈5 на пользователя, запас ×3) |
| Публикации | расчёт 5 млн/сутки; средн. ~60/с; **пик 2 000/с** (ровные часы 09:00, 10:00, 12:00, 18:00 МСК) |
| Точность публикации | p95 ≤ 20 с, p99 ≤ 60 с от `scheduled_at` |
| Доступность | API/веб 99.9 %/мес; контур публикаций 99.95 % |
| Потеря запланированных постов | 0 (RPO данных ≤ 5 мин, публикации — at-least-once + идемпотентность, 0 дублей) |
| RTO | ≤ 1 ч (отказ зоны), ≤ 4 ч (полная потеря кластера, восстановление из бэкапа) |
| Время ответа API | p95 ≤ 300 мс (чтение), ≤ 500 мс (запись) |
| Масштабирование | все сервисы stateless, горизонтально (HPA/KEDA), БД — HA-кластер + реплики чтения |
| Безопасность/закон | ПДн и данные в РФ (152-ФЗ), токены соцсетей шифруются (envelope/KMS), 38-ФЗ (маркировка), 54-ФЗ (чеки) |

## 3. Стек
### Бэкенд (Java)
| Слой | Выбор |
|---|---|
| Язык / рантайм | **Java 21 (LTS)**, Eclipse Temurin; **virtual threads** для блокирующего I/O (HTTP к соцсетям, JDBC, AMQP-листенеры) |
| Фреймворк | **Spring Boot 3.x** (3.4+): Spring Web (MVC, `RestClient`), Spring Security (OAuth2 Client + Resource Server/JWT), Spring Validation, Spring Actuator, Spring WebSocket (STOMP) |
| Данные | **Spring Data JPA / Hibernate 6** для CRUD-домена; `JdbcClient` (или **jOOQ** — решение в ADR) для «горячих» запросов (диспетчер `SKIP LOCKED`, bulk-операции); **Flyway** — миграции |
| Сборка | **Gradle (Kotlin DSL)** multi-module, version catalog `gradle/libs.versions.toml`, convention-плагины в `build-logic/`; Spotless (palantir-java-format), Error Prone + NullAway, JaCoCo, ArchUnit |
| Очереди | **RabbitMQ 3.13 (quorum queues)** через **Spring AMQP**; transactional outbox |
| Устойчивость | **Resilience4j** (Retry, CircuitBreaker, Bulkhead, TimeLimiter, локальный RateLimiter) + **Bucket4j (Redis/Lettuce)** для распределённых лимитов API соцсетей |
| Кэш/локи | **Redis 7** (Spring Data Redis / Lettuce), ShedLock для периодических задач |
| Аналитика | **ClickHouse** (JDBC-драйвер) — метрики постов, события, аудит |
| Файлы/медиа | S3-совместимое хранилище в РФ (AWS SDK v2), FFmpeg (через процесс/JavaCV), обработка изображений (Thumbnailator/libvips-обёртка) |
| API-контракт | **springdoc-openapi** → генерируемый `docs/api/openapi.yaml` (code-first), из него TS-клиент для фронта |
| Наблюдаемость | **Micrometer** (Prometheus registry), Micrometer Tracing + **OpenTelemetry** (OTLP), структурированные JSON-логи Spring Boot, Sentry SDK |
| Тесты | **JUnit 5**, AssertJ, Mockito, **Testcontainers** (`@ServiceConnection`: Postgres, Redis, RabbitMQ, MinIO, ClickHouse), WireMock (адаптеры соцсетей), ArchUnit, Spring REST Docs/MockMvc |
| Образы | **Jib** → distroless Java 21, non-root; JVM: `-XX:MaxRAMPercentage=75`, Generational ZGC для API (или G1 по результатам нагрузки), AppCDS для быстрого старта |

### Фронтенд (TypeScript, без изменений по сути)
Next.js 15 + React 19 + TypeScript, Tailwind + shadcn/ui, TanStack Query, Tiptap, FullCalendar/dnd-kit; **pnpm workspaces** в `frontend/`; клиент API генерируется из `docs/api/openapi.yaml` (openapi-typescript + openapi-fetch). Подробно — `03-frontend.md`.

### Инфраструктура
Kubernetes (Yandex Managed K8s, 3 зоны), Terraform, Helm, **Argo CD** (GitOps) + Argo Rollouts, **GitHub Actions**, **GHCR**, KEDA, PostgreSQL 16 HA, Redis, RabbitMQ, ClickHouse, Object Storage, Prometheus/Grafana/Loki/Tempo, Sentry, Vault. Подробно — `04-devops.md`.

### Платежи и AI
ЮKassa (карты, СБП, SberPay, ЮMoney, T-Pay) + Т-Касса резервно, чеки 54-ФЗ, счета юрлицам + ЭДО. AI — абстракция провайдера LLM (YandexGPT / GigaChat / OpenAI-совместимые) с квотами.

## 4. Архитектура верхнего уровня
```
[Web Next.js] [Mobile] [Public API clients] [Browser ext]
        │ HTTPS (Ingress / WAF / rate limit)
   ┌────▼───────────────────────┐
   │ posts-api (Spring Boot)    │  REST /api/v1 + WebSocket(STOMP) + auth (VK ID, Яндекс ID, email, 2FA, JWT)
   └────┬───────────────────────┘
        │ домен: workspaces, channels, posts, calendar, media, billing, ord, inbox, analytics, automations, ai
        ▼
  PostgreSQL HA ◄── outbox ──► RabbitMQ (quorum)
        ▲   ▲                    │ publish.vk / publish.tg / publish.ok / publish.max / publish.rutube / actions.* / media / notify / events.*
        │   │                    ▼
        │   │   posts-publisher (отдельный Deployment на каждую сеть: publisher-vk, publisher-tg, …)
        │   │        └─ Bucket4j/Redis лимиты, Resilience4j CB/Retry, virtual threads ─► API соцсетей
        │   └── posts-scheduler (диспетчер SKIP LOCKED, повторы, автоудаление/открепление, reaper, продления, token-refresh; ShedLock)
        │
        ├── posts-ingest (вебхуки VK Callback / Telegram / Max → нормализованные события)
        ├── posts-media (загрузка/транскодирование, превью, проверки)
        ├── posts-notifications (email, Telegram-бот, Web Push, WS fan-out)
        └── posts-analytics (Фаза 2: сбор статистики → ClickHouse)
  S3 (медиа)      ClickHouse (метрики/события/аудит)      Redis (лимиты, кэш, локи)
```
Подробности — `02-backend.md` и `04-devops.md`.

## 5. Фазы
### Фаза 0 — Фундамент (недели 1–2)
Монорепо (backend Gradle multi-module, frontend Next.js), docker-compose для локальной разработки, CI, staging-кластер, ADR, первые контроллеры-заглушки и сгенерированный OpenAPI, дизайн-система, тест-стратегия.

### Фаза 1 — MVP (недели 3–12) «Надёжный планировщик для команды»
- Регистрация/вход: email+пароль, VK ID, Яндекс ID; 2FA; восстановление пароля.
- Workspaces, проекты, приглашения, роли (Owner/Admin/Editor/Author/Viewer).
- Каналы: **VK** (группы и личная страница), **Telegram** (каналы/группы через бота), **OK**, **Max**.
- Редактор поста: текст, медиа, варианты под сеть, предпросмотр, валидация, первый комментарий, закреп/открепление, автоудаление, без звука, повторы.
- Контент-календарь (месяц/неделя/список), drag&drop, черновики, наборы каналов.
- Публикационный конвейер: scheduler + RabbitMQ + publisher-сервисы по сетям, ретраи, идемпотентность, live-статусы, уведомления об ошибках.
- Медиатека (базовая) + S3.
- Биллинг: баланс workspace с посуточным списанием по числу каналов (сетка тарифов в БД), ЮKassa (пополнение + автоплатёж), промокоды (бонус на баланс), счета юрлицам; legacy-подписка Free/Старт/Про/Агентство сохранена за фичефлагом на период миграции.
- Базовая аналитика публикаций, аудит-лог, админ-панель.

### Фаза 2 — Превосходство (месяцы 4–6)
Согласование и клиентские ссылки; рубрики и слоты; маркировка рекламы (VK ОРД, ОРД Яндекса); истории VK/OK; пакетная загрузка; Rutube; кросс-сетевая аналитика + PDF-отчёты; единый инбокс; AI-студия; кросспостинг; RSS; граббер; автопостинг предложки VK; антиспам (VK + TG); публичное API + вебхуки; партнёрка; импорт из Впостера.

### Фаза 3 — Экосистема (месяцы 7–12)
Боты, конкурсы, автоворонки, динамические обложки, экспорт товаров, AdsMarket, анонимные сообщения, мобильные приложения, white-label, расширение браузера, интеграции Make/n8n/Albato, ML «лучшее время», новые сети (после юр. оценки).

## 6. Роли агентов и порядок работы
| Агент | Документ | Главный результат |
|---|---|---|
| **Posts Аналитик** | `01-analyst.md` | PRD, user stories с критериями приёмки, бэклог, глоссарий, матрица возможностей сетей |
| **Posts Разраб Бэк** | `02-backend.md` | Java/Spring Boot сервисы, домен, БД, очереди, интеграции соцсетей, биллинг, безопасность, OpenAPI |
| **Posts Разраб Фронт** | `03-frontend.md` | Веб-приложение Next.js, дизайн-система, экраны, TS-клиент API |
| **Posts Devops** | `04-devops.md` | Репо-скелет, CI/CD, окружения, K8s, мониторинг, бэкапы |
| **Posts Тестировщик** | `05-qa.md` | Тест-стратегия, автотесты, нагрузочное и хаос-тестирование |

### Порядок (критический путь)
1. **Devops** (день 1–3): скелет репо — `backend/` (Gradle multi-module), `frontend/` (Next.js, pnpm), `infra/docker-compose`, CI GitHub Actions, `docs/` с анализом и всеми ТЗ → **гейт B** (остальные агенты начинают работу в репо).
2. **Аналитик** (нед. 1, параллельно): PRD MVP, глоссарий, роли, матрица сетей, stories E1–E6 → **гейт A** (утверждение владельцем).
3. **Бэк** (нед. 2): ADR + схема БД (Flyway) + контроллеры-заглушки с DTO ядра (auth, workspaces, channels, posts, media) → сгенерированный `docs/api/openapi.yaml` → ревью Фронта и Тестировщика → **гейт C** (контракт заморожен на спринт).
4. **Фронт** (нед. 1–2): дизайн-система, каркас приложения, TS-клиент + MSW-моки из `openapi.yaml`.
5. **Тестировщик** (нед. 1–2): тест-стратегия, тест-план MVP, каркас e2e (Playwright) и нагрузки (k6), эмулятор соцсетей (вместе с Бэком).
6. Спринты по 2 недели (нед. 3–12): вертикальные срезы (story = бэк + фронт + тесты); Devops — staging (нед. 3–4), прод (нед. 8–10), мониторинг и бэкапы до релиза.
7. **Гейт релиза MVP:** все P0 stories приняты, e2e зелёные, нагрузочный тест 2 000 публикаций/с на staging пройден, восстановление из бэкапа отрепетировано, алерты работают.

### Правила взаимодействия
- Источники правды: требования — `docs/product/` (Аналитик); контракт API — **генерируется из бэкенда** в `docs/api/openapi.yaml` (Бэк; файл коммитится, CI проверяет актуальность и breaking changes); инфраструктура — `infra/` (Devops); тесты — `backend/**/src/test`, `frontend/**/__tests__`, `tests/` и `docs/qa/` (Тестировщик).
- Каждая задача — GitHub Issue с метками `role:analyst|backend|frontend|devops|qa`, `phase:0|1|2|3`, `P0|P1|P2`.
- Ветки: `feat/<issue>-<slug>`, `fix/...`, `chore/...`; Conventional Commits; PR — зелёный CI + 1 ревью (агент другой роли или владелец); `main` защищён; релизы — теги `vX.Y.Z`.
- Изменение API: PR меняет Java-контроллеры/DTO → перегенерирует `openapi.yaml` и TS-клиент → метка `contract-change` при несовместимых изменениях, уведомление Фронта и Тестировщика.
- Definition of Done: код + тесты (покрытие домена ≥ 80 % по JaCoCo / Vitest), миграции обратно совместимы (expand/contract), метрики/логи/трейсы добавлены, документация обновлена, рискованные фичи — за feature flag.

## 7. Структура монорепо
```
posts/
  backend/                         # Gradle (Kotlin DSL) multi-module, Java 21
    settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml  gradlew
    build-logic/                   # convention-плагины: java-conventions, spring-app, test-conventions
    libs/
      common/                      # ошибки RFC 7807, UUIDv7, время/TZ, JSON, утилиты
      domain/                      # доменная модель и сервисы (posts, channels, workspaces, billing…), порты
      persistence/                 # JPA-сущности, репозитории, JdbcClient-запросы, Flyway-миграции (db/migration)
      messaging/                   # Spring AMQP: топология, сообщения, outbox-relay
      security/                    # JWT, RBAC, шифрование токенов (envelope/KMS)
      ratelimit/                   # Bucket4j/Redis + Resilience4j конфигурация
      content/                     # рендеринг контента под сети (TG HTML, Max HTML, plain), лимиты сетей
      social-api/                  # интерфейс SocialAdapter, общая модель, классификация ошибок
      social-vk/  social-telegram/  social-ok/  social-max/  social-rutube/   # адаптеры
      test-support/                # Testcontainers-конфиги, фикстуры, эмулятор-клиенты
    apps/
      api/                         # posts-api: REST + WebSocket + auth
      scheduler/                   # posts-scheduler
      publisher/                   # posts-publisher (один образ, отдельный Deployment на сеть)
      media/                       # posts-media
      ingest/                      # posts-ingest
      notifications/               # posts-notifications
      analytics/                   # posts-analytics (Фаза 2)
  frontend/                        # pnpm workspaces
    apps/web/                      # Next.js
    packages/ui/                   # дизайн-система
    packages/api-client/           # сгенерированный TS-клиент из docs/api/openapi.yaml
    packages/domain/               # предпросмотр/конвертеры форматирования для UI, общие TS-типы
  infra/
    docker-compose/                # локальная разработка
    terraform/  helm/  argocd/  k6/
  tests/
    e2e/ (Playwright)  load/ (k6)  social-emulator/  chaos/
  docs/
    analysis.md  specs/ (00…05, agent-tasks.md)  product/  adr/  api/ (openapi.yaml, fixtures/)  qa/  runbooks/
  .github/workflows/  .github/ISSUE_TEMPLATE/  CODEOWNERS
```
