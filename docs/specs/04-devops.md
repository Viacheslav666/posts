# 04. ТЗ для агента «Posts Devops» (инфраструктура, CI/CD, надёжность)

Прочитать: `00-overview.md`, `02-backend.md` (сервисы, ёмкость), `agent-tasks.md`. Код: `infra/`, `.github/`, `docs/runbooks/`, корневые файлы репо. Бэкенд - Java 21 / Spring Boot 3 (Gradle multi-module в `backend/`), фронтенд - Next.js (pnpm в `frontend/`).

## 1. Цели
- Платформа для 100 000 пользователей с запасом ×3: доступность API 99.9 %, контура публикаций 99.95 %, RPO ≤ 5 мин, RTO ≤ 1 ч (зона) / ≤ 4 ч (регион-катастрофа из бэкапов).
- Полностью воспроизводимая инфраструктура (IaC), GitOps-деплой, деплой без простоя, откат ≤ 5 мин.
- Данные и ПДн в РФ (152-ФЗ).

## 2. Репозиторий и GitHub (первая задача)
1. Репозиторий **`Viacheslav666/posts`** (приватный) создаёт владелец (или основной агент с его согласия); облачные агенты Cursor получают к нему доступ. Devops-агент первым PR создаёт скелет (см. `agent-tasks.md`, задача DEV-1).
2. Структура монорепо - по `00-overview.md` §7: `backend/` (Gradle Kotlin DSL, Java 21), `frontend/` (pnpm workspaces, Next.js), `infra/`, `tests/`, `docs/`.
3. Защита `main`: PR обязателен, 1 approve, обязательные проверки (`backend`, `frontend`, `contract`, `security`), запрет force-push, squash merge, CODEOWNERS: `backend/**` → backend, `frontend/**` → frontend, `infra/**` и `.github/**` → devops, `docs/product/**` → analyst, `tests/**` и `docs/qa/**` → qa, `docs/api/openapi.yaml` → backend + frontend.
4. Шаблоны: Issue (story/bug/task/ADR), PR (чеклист DoD), метки `role:*`, `phase:*`, `P0–P2`, `contract-change`; GitHub Project-доска (Backlog → Ready → In progress → Review → QA → Done).
5. Renovate/Dependabot (Gradle version catalog, pnpm, Docker, GitHub Actions; еженедельно, группировка Spring/Testcontainers), CodeQL (java + javascript-typescript), secret scanning + push protection, `gitleaks` в CI.
6. Локальная разработка: `infra/docker-compose/docker-compose.yml` (Postgres 16, Redis 7, RabbitMQ 3.13-management, MinIO + создание бакетов, ClickHouse, Mailpit, OTel collector + `grafana/otel-lgtm`, эмулятор соцсетей из `tests/social-emulator`), `Makefile` с целями `make up`, `make down`, `make backend` (`./gradlew :apps:api:bootRun --args='--spring.profiles.active=local'`), `make frontend` (`pnpm --dir frontend dev`), `make test`, `make openapi` (генерация `docs/api/openapi.yaml` + TS-клиента); `.env.example`; seed-данные через Flyway `R__seed_local.sql` (только профиль `local`). Цель: агент поднимает окружение одной командой за ≤ 10 мин. Для облачных агентов Cursor - `.cursor/environment.json` (или аналог) с установкой JDK 21, Node 22, pnpm, Docker и командами прогрева кэшей.

## 3. CI/CD (GitHub Actions)
### CI на каждый PR (`.github/workflows/ci.yml`), цель ≤ 12 мин
Фильтр путей (`dorny/paths-filter`): джобы бэка/фронта запускаются только при изменениях своих каталогов (+ всегда на `main`).
- **backend** (ubuntu-latest, Docker доступен для Testcontainers): `actions/setup-java` (Temurin 21) + `gradle/actions/setup-gradle` (кэш, configuration cache) → `./gradlew spotlessCheck build integrationTest jacocoTestCoverageVerification` (unit, интеграционные на Testcontainers, ArchUnit, Error Prone); публикация JUnit-отчётов и покрытия в PR.
- **contract**: `./gradlew :apps:api:generateOpenApiDocs` → `git diff --exit-code docs/api/openapi.yaml`; `spectral lint`; `oasdiff breaking` против `main` → при breaking требуется метка `contract-change`; генерация TS-клиента (`pnpm --dir frontend gen:api`) → `git diff --exit-code frontend/packages/api-client`.
- **migrations**: Flyway migrate на пустой Postgres и на снапшоте схемы staging (Testcontainers), squawk-линтер на блокирующие операции.
- **frontend**: `pnpm install --frozen-lockfile` (кэш store) → lint, typecheck, Vitest, `next build`, Storybook build (по изменениям `packages/ui`).
- **e2e** (на merge в `main`, ночью и по метке `e2e`): docker-compose (инфраструктура + эмулятор) + образы бэка (`./gradlew jibDockerBuild`) + `next start` → Playwright.
- **security**: CodeQL, osv-scanner (Gradle lockfiles + pnpm-lock), gitleaks, Trivy (FS и образы), hadolint (если есть Dockerfile’ы, например для web).
- **images** (на merge в `main` и тегах): `./gradlew jib` для `posts-api`, `posts-scheduler`, `posts-publisher`, `posts-media` (база с ffmpeg), `posts-ingest`, `posts-notifications` → `ghcr.io/viacheslav666/posts-<svc>:<sha>`; `posts-web` - Dockerfile Next.js standalone (node:22-alpine, non-root); SBOM (syft/CycloneDX Gradle-плагин), подпись **cosign** (keyless OIDC).
- Включить Gradle dependency locking и проверку подписей зависимостей (`verification-metadata.xml`) - защита цепочки поставок.

### CD (GitOps)
- Merge в `main` → образы `:<sha>` → бот (Argo CD Image Updater или job в Actions) обновляет теги в `infra/argocd/envs/staging` → **Argo CD** синхронизирует staging.
- После зелёных smoke/e2e на staging - релиз тегом `vX.Y.Z` (release-please) → PR в `infra/argocd/envs/production` → ручной approve владельца/дежурного → Argo CD.
- **Argo Rollouts canary** для `api` и воркеров (10 % → 50 % → 100 %, анализ ошибок/латентности/задержки публикаций из Prometheus, автооткат).
- Миграции Flyway: отдельный Kubernetes Job (Argo CD PreSync hook) из образа `posts-api` с `--spring.flyway.enabled=true --spring.main.web-application-type=none`, только expand-изменения; в приложениях `spring.flyway.enabled=false`.
- Окружения: `local` (docker-compose), `preview` (опционально, эфемерные для фронта), `staging`, `production`.

## 4. Инфраструктура (облако в РФ)
Рекомендация: **Yandex Cloud** (альтернатива - Selectel/VK Cloud). Всё описать в **Terraform** (`infra/terraform/{modules,envs}`), state в S3 с блокировкой.
| Компонент | Прод | Примечание |
|---|---|---|
| Kubernetes | Managed K8s, регион ru-central1, **3 зоны**; node pools: `system`, `api` (4–20 нод), `workers` (4–40 нод, autoscaler), `media` (CPU-оптимизированные, spot/preemptible допустимы) | Cluster Autoscaler, PodDisruptionBudget, topologySpreadConstraints по зонам; JVM-поды: requests = limits по памяти, `-XX:MaxRAMPercentage=75`, `-XX:+ExitOnOutOfMemoryError` |
| PostgreSQL | Managed PostgreSQL 16 HA: primary + 2 реплики в разных зонах (или Patroni/CloudNativePG в K8s), 16 vCPU/64 ГБ старт, NVMe | PgBouncer (в кластере, 2+ реплики), pg_partman, pg_stat_statements, автопереключение < 60 с |
| Redis | Managed Redis (Sentinel/HA, 3 узла) | отдельные инстансы: `cache` (можно терять) и `ratelimit/locks` |
| RabbitMQ | Кластер 3 узла (RabbitMQ Cluster Operator), quorum queues, разнесение по зонам, PV на SSD | мониторинг очередей, политики DLX, лимиты памяти/диска |
| ClickHouse | Managed ClickHouse, 2 реплики (шардинг при росте) | метрики постов, события, аудит |
| Объектное хранилище | Yandex Object Storage, версионирование для бэкапов, lifecycle-политики | presigned URL, CDN для медиа и статики |
| Ingress | Yandex ALB / ingress-nginx за L7-балансировщиком, **WAF + DDoS-защита**, TLS (cert-manager, Let’s Encrypt) | rate limit на уровне ingress |
| DNS/CDN | Cloud DNS, CDN для `static.` и `media.` | |
| Секреты | **HashiCorp Vault** или Lockbox + External Secrets Operator; KMS для envelope encryption токенов | никаких секретов в репо/образах |
| Email | транзакционный провайдер в РФ (Unisender Go / Mailganer / Yandex Postbox), SPF/DKIM/DMARC | |

## 5. Масштабирование
- Deployment’ы (Helm-чарт `infra/helm/posts`, один чарт с секциями на сервис): `api`, `scheduler`, `publisher-vk`, `publisher-tg`, `publisher-ok`, `publisher-max`, `publisher-rutube` (один образ `posts-publisher`, разный `POSTS_PUBLISHER_NETWORK`, своя очередь, свои ресурсы и лимиты масштабирования), `media`, `ingest`, `notifications`, `web` (Next.js), позже `analytics`.
- HPA для `api` и `web` (CPU 60 % + RPS на под через Prometheus Adapter); **KEDA** для воркеров по длине очередей RabbitMQ (`publish.vk`, `publish.tg`, … отдельно; `media`, `notify`, `ingest`).
- Минимум реплик: api 3, scheduler 2, каждый publisher 2, ingest 2, notifications 2, web 3 - по разным зонам.
- JVM-специфика: probes - `startupProbe` на `/actuator/health/liveness` (до 60 с), `readinessProbe` на `/actuator/health/readiness`; старт < 10 с за счёт AppCDS/Spring AOT; KEDA `minReplicaCount` достаточный, чтобы не зависеть от холодного старта в пике.
- Пиковые часы (09:00–10:00, 12:00, 18:00 МСК): **pre-scaling** через KEDA cron-триггер за 5 мин до ровного часа для `scheduler` и `publisher-*`.
- requests/limits у всех подов, PriorityClass: publisher/scheduler выше, чем analytics/media.
- Соединения: PgBouncer (transaction mode) перед primary; HikariCP на под 20–40; `max_connections` ≤ 500; отдельный пул/DSN на реплики чтения.

## 6. Наблюдаемость
- **Метрики:** Prometheus (kube-prometheus-stack) или VictoriaMetrics; Spring Boot Actuator `/actuator/prometheus` (Micrometer: JVM heap/GC/потоки, HikariCP, RabbitMQ-листенеры, Resilience4j CircuitBreaker/Retry/Bulkhead, Bucket4j, HTTP-клиенты) + экспортеры Postgres, PgBouncer, Redis, RabbitMQ, ClickHouse, ingress, node.
- **Логи:** Loki (или ELK/OpenSearch), структурированные JSON-логи Spring Boot (`logging.structured.format.console=ecs`), retention 30 дней (аудит - в ClickHouse 1 год).
- **Трейсы:** Micrometer Tracing (OTel bridge, OTLP) из Spring Boot → OpenTelemetry Collector → Tempo, контекст через HTTP и заголовки AMQP; семплирование 10 % + 100 % ошибок (tail sampling в collector). JFR-профилирование по запросу (async-profiler/Pyroscope опционально).
- **Ошибки:** Sentry (self-hosted или sentry-совместимый GlitchTip) для фронта и бэка.
- **Дашборды Grafana (as code):** Golden signals API; JVM (heap, GC-паузы, виртуальные потоки/pinning, HikariCP pending); **Публикации** (запланировано/опубликовано/ошибки по сетям, задержка `published_at - run_at` p50/p95/p99, глубина очередей, ретраи, DLQ, throttling по сетям); БД (TPS, репликационный лаг, медленные запросы, bloat); биллинг (успешные/неуспешные платежи); бизнес (регистрации, активные каналы).
- **SLO и алерты** (Alertmanager → Telegram-чат дежурных + email; критичные - звонок через сервис оповещений):
  | Алерт | Условие | Severity |
  |---|---|---|
  | PublishLatencyHigh | p99 задержки публикации > 60 с 5 мин | critical |
  | PublishErrorRate | доля `failed_permanent` по вине сервиса > 1 % 10 мин (по сети) | critical |
  | QueueBacklog | `publish.*` > 10 000 или возраст старшего сообщения > 2 мин | critical |
  | DLQNotEmpty | сообщения в DLQ > 0 15 мин | warning |
  | SchedulerStalled | нет диспетчеризаций 2 мин при наличии due | critical |
  | API5xx | > 1 % 5 мин / p95 > 1 с | critical / warning |
  | PGReplicationLag | > 30 с | warning; > 5 мин critical |
  | PGDiskUsage | > 80 % | warning |
  | BackupFailed | последний успешный бэкап > 26 ч / WAL-архивирование отстаёт > 10 мин | critical |
  | CertExpiry | < 14 дней | warning |
  | CircuitBreakerOpen | CB сети открыт > 2 мин | critical |
  | HikariPending | ожидающие соединения > 0 более 1 мин | warning |
  | JvmGcPause | GC-паузы > 500 мс или heap > 90 % 5 мин | warning |
  | ChannelReauthSpike | рост `reauth_required` ×5 к норме (массовый отзыв токенов сетью) | warning |
- Error budget-политика: при исчерпании бюджета SLO - заморозка фич, только надёжность.
- **Публичная status-страница** (Upptime на GitHub Pages или Uptime Kuma/Instatus) с проверками API, веба, публикаций (синтетический пост в тестовый канал каждые 5 мин на каждую сеть).

## 7. Бэкапы и аварийное восстановление
- PostgreSQL: непрерывное WAL-архивирование + ежедневный полный бэкап (**WAL-G/pgBackRest** или встроенные бэкапы managed-сервиса) в Object Storage **в другом регионе/бакете с object lock**; PITR 14 дней; ежемесячные бэкапы хранятся 12 мес. RPO ≤ 5 мин.
- ClickHouse: ежедневный `BACKUP` в S3, 30 дней.
- RabbitMQ: определения (exchanges/queues/policies) в Git; сообщения не бэкапим (источник истины - PG, очередь восстанавливается scheduler’ом из `publications`).
- Redis: не критичен (кэш/лимиты); `ratelimit` - AOF everysec.
- S3-медиа: версионирование + репликация в резервный бакет.
- Vault/секреты: снапшоты, ключи unseal у владельца.
- **Учения:** ежемесячно автоматический restore последнего бэкапа в изолированную БД + проверочные запросы (job в CI, отчёт в Telegram); ежеквартально DR-учения (потеря зоны: drain нод зоны; отказ primary: failover), фиксировать фактические RTO/RPO.
- Runbook’и в `docs/runbooks/`: failover PG, восстановление PITR, переполнение очереди, массовый отзыв токенов VK, деградация сети-соцсети, откат релиза, ротация секретов, инцидент безопасности.

## 8. Безопасность инфраструктуры
- Сети: приватные подсети для БД/очередей, доступ только из K8s; bastion/VPN (WireGuard) для администраторов; Security Groups по принципу минимальных прав.
- K8s: NetworkPolicies (default deny), Pod Security Standards `restricted`, read-only root FS, non-root, без привилегий, Kyverno/Gatekeeper политики (только подписанные образы из GHCR, обязательные limits), RBAC по ролям, аудит-логи кластера.
- Шифрование: TLS везде (включая внутри - mTLS опционально через Linkerd на Фазе 2), шифрование дисков и бакетов, KMS.
- Ротация секретов каждые 90 дней, токены CI с минимальными правами (OIDC GitHub → облако, без долгоживущих ключей).
- Сканирование образов в реестре, еженедельный пересбор базовых образов.
- Логи доступа к ПДн; соответствие 152-ФЗ (уведомление РКН об обработке ПДн делает владелец - подготовить чеклист).

## 9. Стоимость (ориентир для старта прода, уточнить калькулятором облака)
Старт (до ~20k пользователей): 3 ноды api/workers по 8 vCPU, managed PG 8 vCPU/32 ГБ HA, Redis, RabbitMQ 3×2 vCPU, ClickHouse минимальный, S3 1–5 ТБ - порядок 150–250 тыс. ₽/мес. Масштабирование до 100k - линейно по нодам воркеров и размеру PG; настроить бюджеты и алерты расходов.

## 10. Задачи по порядку (детально - `agent-tasks.md`)
1. (Дни 1–3) **DEV-1** скелет монорепо: `backend/` Gradle multi-module (build-logic, version catalog, модули-заглушки, приложение `api` с `/actuator/health`), `frontend/` Next.js (pnpm workspaces), `infra/docker-compose`, Makefile, CI (`backend`, `frontend`, `contract`, `security`), CODEOWNERS, шаблоны, `docs/` с анализом и всеми ТЗ. **Гейт B.**
2. (Нед. 1–2) Jib-сборка образов всех приложений + Dockerfile web → GHCR, cosign, SBOM, Trivy; Renovate; branch protection.
3. (Нед. 3–4) Terraform: сеть, K8s staging, managed PG/Redis/S3, RabbitMQ operator, ClickHouse; Argo CD + Helm-чарт `posts` с values по окружениям; External Secrets + Vault; cert-manager, ingress, DNS; Flyway PreSync Job.
4. (Нед. 4–5) Наблюдаемость: Prometheus/Grafana/Loki/Tempo/Sentry, дашборды (включая JVM и конвейер публикаций) и алерты §6, Telegram-канал алертов.
5. (Нед. 5–6) KEDA/HPA, PDB, topology spread, PriorityClass, pre-scaling; нагрузочный стенд (k6-operator) для QA.
6. (Нед. 6–8) Бэкапы, PITR, автоматический restore-тест, runbook’и, status-страница.
7. (Нед. 8–10) Production, WAF/DDoS, canary (Argo Rollouts), DR-учения, security-review, финальный нагрузочный тест с QA. **Гейт релиза MVP.**
8. (Пост-MVP) FinOps, mTLS, warm-standby PG в другом регионе.

## 11. Definition of Done для devops
Всё в коде (Terraform/Helm/Argo), PR с `terraform plan` в комментарии, нет ручных изменений в облаке (drift-check еженедельно), для каждого нового компонента: метрики, алерты, runbook, бэкап (если хранит состояние).
