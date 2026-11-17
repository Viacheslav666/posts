# Первые задачи для облачных агентов Cursor — репозиторий `Viacheslav666/posts`

Каждая задача ниже = **одна ветка + один Pull Request** в `main`. Формулировку задачи можно целиком передавать облачному агенту Cursor как промпт (вместе с «Общей вводной»). Задачи внутри роли выполняются по порядку; зависимости между ролями указаны в поле «Зависит от».

## Общая вводная (добавлять в начало промпта каждой задачи)
> Ты — агент «Posts <роль>» проекта Posts (SMM-платформа, аналог и конкурент vposter.ru). Репозиторий: `Viacheslav666/posts`. Перед работой прочитай `docs/specs/00-overview.md`, ТЗ своей роли (`docs/specs/0X-*.md`) и `docs/analysis.md`. Стек: бэкенд Java 21 + Spring Boot 3 (Gradle Kotlin DSL multi-module в `backend/`), фронтенд Next.js 15 + TypeScript (pnpm в `frontend/`), инфраструктура в `infra/`, документация в `docs/`. Требование надёжности: 100 000 пользователей с запасом ×3, публикации без потерь и дублей. Правила: ветка `feat|fix|chore/<id>-<slug>`, Conventional Commits, PR с описанием, чеклистом DoD и ссылкой на задачу; CI должен быть зелёным; не меняй файлы чужой зоны без необходимости (CODEOWNERS), при необходимости — отметь в PR. Документы пиши на русском, код и идентификаторы — на английском. Не коммить секреты.

**Предусловие:** репозиторий `Viacheslav666/posts` создан владельцем (приватный), облачные агенты Cursor подключены к нему с правом push в ветки и создания PR.

---

## Posts Devops

### DEV-1. Скелет монорепозитория (первая задача проекта)
- **Ветка:** `chore/dev-1-repo-skeleton` · **Зависит от:** — · **Блокирует:** все остальные задачи.
- **Сделать:**
  1. Корень: `README.md` (что за проект, структура, быстрый старт), `.gitignore` (Java/Gradle, Node/Next, IDE, `.env`), `.editorconfig`, `LICENSE` (proprietary/«All rights reserved»), `Makefile` (`up`, `down`, `backend`, `frontend`, `test`, `openapi`, `lint`), `.env.example`, `CODEOWNERS` (зоны ролей по `04-devops.md` §2), `.github/ISSUE_TEMPLATE/{story,bug,task,adr}.md`, `.github/pull_request_template.md` (чеклист DoD).
  2. `docs/`: положить `analysis.md` и `specs/` (00-overview, 01-analyst, 02-backend, 03-frontend, 04-devops, 05-qa, agent-tasks) — файлы приложены владельцем к задаче; создать пустые разделы с `README.md`: `docs/product/`, `docs/adr/` (+ шаблон ADR `0000-template.md`), `docs/api/` (+ `fixtures/formatting/.gitkeep`), `docs/qa/`, `docs/runbooks/`.
  3. `backend/` — Gradle 8.x (Kotlin DSL) multi-module, Java 21 toolchain:
     - `settings.gradle.kts`, корневой `build.gradle.kts`, `gradle/libs.versions.toml` (Spring Boot 3.4.x+, springdoc 2.x, Resilience4j, Bucket4j, Flyway, Testcontainers, WireMock, ArchUnit, ShedLock), Gradle wrapper.
     - `build-logic/` с convention-плагинами `posts.java-conventions` (Spotless palantir-java-format, Error Prone, JUnit 5, JaCoCo), `posts.spring-app` (Spring Boot + Jib), `posts.integration-test` (source set `integrationTest`).
     - Модули-заглушки (по одному классу/`package-info.java`): `libs/{common,domain,persistence,messaging,security,ratelimit,content,social-api,social-vk,social-telegram,social-ok,social-max,test-support}`, `apps/{api,scheduler,publisher,media,ingest,notifications}`.
     - `apps/api`: Spring Boot-приложение с Actuator (`/actuator/health`), springdoc, эндпоинт `GET /api/v1/ping` → `{"status":"ok"}`, профиль `local`, один unit-тест и один интеграционный тест на Testcontainers Postgres (`@ServiceConnection`) с Flyway-миграцией `V1__init.sql` (таблица `schema_info` или пустая baseline).
     - Задача `:apps:api:generateOpenApiDocs` → `docs/api/openapi.yaml` (закоммитить результат).
     - Остальные `apps/*` — минимальные Spring Boot-приложения с Actuator, которые стартуют (`contextLoads`-тест).
     - ArchUnit-тест базовых правил зависимостей (`apps` не зависят друг от друга, `domain` не зависит от `apps`).
  4. `frontend/` — pnpm workspaces (`pnpm-workspace.yaml`), Node 22 (`.nvmrc`):
     - `apps/web`: Next.js 15 (App Router, TypeScript strict, Tailwind, ESLint, Prettier), главная страница-заглушка «Posts», страница `/health`; Vitest с одним тестом.
     - `packages/ui` (заглушка с одним компонентом Button на shadcn/ui), `packages/domain` (заглушка), `packages/api-client`: скрипт `pnpm gen:api` = `openapi-typescript ../../docs/api/openapi.yaml -o src/schema.d.ts` + обёртка `openapi-fetch`; сгенерированный код закоммитить.
  5. `infra/docker-compose/docker-compose.yml`: Postgres 16, Redis 7, RabbitMQ 3.13-management, MinIO (+ init-контейнер создания бакета `posts-media`), ClickHouse, Mailpit, `grafana/otel-lgtm`; healthchecks; именованные volume; порты по умолчанию; `application-local.yml` в `apps/api` настроен на эти сервисы.
  6. `tests/`: каталоги `e2e/`, `load/`, `social-emulator/`, `chaos/` с `README.md` (заполнит QA).
  7. CI `.github/workflows/ci.yml`: path-filter; джобы `backend` (Temurin 21, setup-gradle, `./gradlew spotlessCheck build integrationTest`), `contract` (генерация OpenAPI + `git diff --exit-code docs/api/openapi.yaml frontend/packages/api-client`), `frontend` (pnpm install, lint, typecheck, test, build), `security` (gitleaks, CodeQL java + javascript-typescript — отдельный workflow `codeql.yml`). Кэширование Gradle и pnpm.
  8. `.cursor/` — инструкции для облачных агентов (например, `.cursor/environment.json` или `README` для агента): JDK 21, Node 22, pnpm, Docker; команды `make up`, `./gradlew build`, `pnpm --dir frontend install`.
- **Критерии приёмки:** на чистой машине `make up && make backend` поднимает API, `curl localhost:8080/api/v1/ping` → ok; `make frontend` открывает страницу на `localhost:3000`; `./gradlew build integrationTest` и `pnpm --dir frontend -r test` зелёные; CI в PR зелёный; `docs/` содержит анализ и все ТЗ; README описывает запуск за ≤ 10 мин.

### DEV-2. Защита веток, метки, доска, Renovate
- **Ветка:** `chore/dev-2-repo-governance` · **Зависит от:** DEV-1.
- **Сделать:** `renovate.json` (группы: Spring Boot, Testcontainers, Next/React, GitHub Actions; расписание еженедельно); файл `docs/runbooks/repo-settings.md` с точными настройками branch protection (обязательные проверки `backend`, `frontend`, `contract`, `security`; 1 approve; squash; запрет force-push), списком меток (`role:*`, `phase:*`, `P0–P2`, `contract-change`) и колонок GitHub Project; скрипт `infra/scripts/gh-labels.sh` (gh CLI) для создания меток. Применение настроек репозитория выполняет владелец (или агент, если у него есть права и владелец подтвердил).
- **Приёмка:** Renovate-конфиг валиден; скрипт меток идемпотентен; документ настроек полный.

### DEV-3. Сборка и публикация образов
- **Ветка:** `chore/dev-3-images` · **Зависит от:** DEV-1.
- **Сделать:** Jib-конфигурация для всех `apps/*` (distroless Java 21 nonroot; для `media` — базовый образ с ffmpeg), JVM-флаги (`-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`), labels OCI; `frontend/apps/web/Dockerfile` (Next.js standalone, node:22-alpine, non-root); workflow `images.yml` (на push в `main` и теги): push в `ghcr.io/viacheslav666/posts-<svc>:<sha>` и `:<tag>`, SBOM (CycloneDX), подпись cosign keyless, скан Trivy (fail на CRITICAL).
- **Приёмка:** образы собираются и запускаются локально (`./gradlew jibDockerBuild`, `docker run` → health OK), workflow проходит в GHCR.

### DEV-4. Локальное окружение для e2e и эмулятора
- **Ветка:** `chore/dev-4-compose-full` · **Зависит от:** DEV-3, QA-3 (эмулятор).
- **Сделать:** профиль compose `full` — все backend-приложения из локально собранных образов + web + эмулятор соцсетей; `make up-full`; конфиг публикатора на эмулятор.
- **Приёмка:** `make up-full` поднимает всю систему; ping API и web доступны; документация в README.

### DEV-5. Staging (Terraform + K8s + Argo CD)
- **Ветка:** `feat/dev-5-staging` · **Зависит от:** DEV-3; доступ к облаку (Yandex Cloud) от владельца.
- **Сделать:** по `04-devops.md` §4, §3 CD: Terraform-модули (сеть, Managed K8s 3 зоны, Managed PostgreSQL HA, Redis, Object Storage), RabbitMQ Cluster Operator, Helm-чарт `infra/helm/posts` (Deployment’ы api, scheduler, publisher-vk/tg/ok/max, media, ingest, notifications, web; probes, PDB, topology spread, ресурсы), Argo CD Application для staging, External Secrets, cert-manager, ingress, Flyway PreSync Job.
- **Приёмка:** `terraform plan` в PR; после применения staging доступен по HTTPS, Argo CD синхронизирован, health всех сервисов зелёный.

### DEV-6. Наблюдаемость
- **Ветка:** `feat/dev-6-observability` · **Зависит от:** DEV-5.
- **Сделать:** kube-prometheus-stack/Loki/Tempo/OTel Collector, Sentry (или GlitchTip), дашборды as code (API golden signals, JVM, конвейер публикаций, БД, RabbitMQ), алерты из `04-devops.md` §6 → Telegram, Upptime/Uptime Kuma status-страница.
- **Приёмка:** дашборды показывают метрики staging; тестовый алерт приходит в Telegram.

---

## Posts Разраб Бэк

### BE-1. ADR и архитектурный каркас
- **Ветка:** `docs/be-1-adr` · **Зависит от:** DEV-1.
- **Сделать:** `docs/adr/0001-java-spring-stack.md`, `0002-module-structure.md`, `0003-multitenancy.md`, `0004-publishing-pipeline.md` (scheduler SKIP LOCKED → RabbitMQ → publisher per network, идемпотентность, ретраи через БД), `0005-rabbitmq-topology.md` (exchanges, очереди, DLX, quorum), `0006-token-encryption.md`, `0007-jpa-vs-jooq-hot-path.md`; ArchUnit-правила из `02-backend.md` §1 п.8 в `libs/test-support`.
- **Приёмка:** ADR согласованы владельцем/ревьюером; ArchUnit-тесты в CI.

### BE-2. Схема БД ядра и сущности
- **Ветка:** `feat/be-2-core-schema` · **Зависит от:** BE-1.
- **Сделать:** Flyway-миграции по `02-backend.md` §4 (users, identities, sessions, workspaces, members, invitations, projects, social_accounts, channels, channel_sets, posts, post_variants, media_assets, publications (партиционирована по месяцам), publication_attempts, scheduled_actions, outbox, audit_log, notifications, idempotency_requests, plans, subscriptions, payments, ledger_entries); JPA-сущности и репозитории в `libs/persistence`; UUIDv7-генератор в `libs/common`; интеграционные тесты миграций и репозиториев на Testcontainers.
- **Приёмка:** `./gradlew integrationTest` зелёный; Hibernate `ddl-auto=validate` проходит; squawk без критичных замечаний.

### BE-3. Контракт ядра (контроллеры-заглушки + DTO) → OpenAPI
- **Ветка:** `feat/be-3-core-contract` · **Зависит от:** BE-2. **Блокирует:** FE-3, QA-2.
- **Сделать:** DTO (`record` + Bean Validation + `@Schema`) и контроллеры (возвращают 501, кроме `/me` с тестовыми данными в профиле `local`) для auth, me, workspaces, members, invitations, projects, channels, channel-sets, networks/capabilities, posts, calendar, publications, media uploads, notifications, billing plans; `ProblemDetail`-ошибки с `code`; курсорная пагинация; перегенерировать `docs/api/openapi.yaml` и TS-клиент; эталонные фикстуры форматирования в `docs/api/fixtures/formatting/` (первые 20 кейсов вместе с QA).
- **Приёмка:** контракт проверен Фронтом и QA в ревью PR (гейт C); у всех операций `operationId`, описания, примеры.

### BE-4. Прототип публикационного конвейера (главный риск)
- **Ветка:** `feat/be-4-pipeline-prototype` · **Зависит от:** BE-2, QA-3 (эмулятор; до его готовности — WireMock).
- **Сделать:** `apps/scheduler` (диспетчер `SKIP LOCKED` батчами, отправка с publisher confirms, reaper), `apps/publisher` (`POSTS_PUBLISHER_NETWORK`, listener на виртуальных потоках, переход состояний, идемпотентность, классификация ошибок, ретраи через `next_attempt_at`), Bucket4j-лимитер на Redis, Resilience4j CircuitBreaker/TimeLimiter, адаптер-заглушка `social-telegram` против эмулятора; метрики `posts_publication_delay_seconds`, `posts_publications_total{network,result}`.
- **Приёмка:** интеграционный тест: 10 000 публикаций на одну минуту → все опубликованы, 0 дублей в эмуляторе, p99 задержки < 60 с локально; тесты TC-PUB-02/03/06/07 (`05-qa.md`) автоматизированы.

### BE-5. Аутентификация
- **Ветка:** `feat/be-5-auth` · **Зависит от:** BE-3.
- **Сделать:** регистрация/подтверждение email/вход/refresh с ротацией и reuse-детектом/выход, JWT ES256 + JWKS, Argon2, 2FA TOTP + резервные коды, VK ID и Яндекс ID (OAuth2 Client, PKCE), восстановление пароля, сессии, Bucket4j-ограничение логина, письма через Mailpit локально.
- **Приёмка:** TC-AUTH-01…07 зелёные (интеграционные тесты); OpenAPI обновлён.

### BE-6. Workspaces, проекты, команда, RBAC, аудит
- **Ветка:** `feat/be-6-workspaces-rbac` · **Зависит от:** BE-5.
- **Сделать:** CRUD workspaces/проектов, приглашения (email, 7 дней), роли, `PermissionEvaluator` по матрице `01-analyst.md`, фильтрация по `workspace_id`, аудит-лог.
- **Приёмка:** автотест RBAC-матрицы и IDOR (TC-RBAC, TC-IDOR) зелёные.

Далее (в порядке `02-backend.md` §12): BE-7 каналы VK/TG/OK/Max · BE-8 медиа + posts-media · BE-9 посты/варианты/рендер/календарь · BE-10 конвейер в прод-качестве · BE-11 уведомления · BE-12 биллинг ЮKassa · BE-13 админ-API · BE-14 харденинг и нагрузка.

### BE-15. ОРД и маркировка рекламы + полный паритет опций поста (E10)
- **Ветка:** `feat/be-15-ord-ad-marking` · **Зависит от:** BE-9, BE-10, **AN-P**.
- **Сделать:** по `docs/product/features/scheduled-posts-and-ad-marking.md` v1.0.1+: Flyway `ord_*`, `OrdProvider` с **полноценными** адаптерами VK ОРД и ОРД Яндекс (не stub; ОЗОН/др. — позже через тот же интерфейс); клиентский API-ключ ОРД (Posts не субсидирует); register creative → erid, вставка маркера, job актов/статистики, расширение DTO поста (Donut, co-authors, poll, stop/del_task, ads); OpenAPI + WireMock ORD.
- **Приёмка:** TC-AD-* (интеграционные) зелёные; fail-closed без erid; идемпотентность `external_id`.

### BE-16. Отложенные истории VK/OK (E11)
- **Ветка:** `feat/be-16-scheduled-stories` · **Зависит от:** BE-7, BE-8, BE-15 (ads), **AN-P**.
- **Сделать:** по `docs/product/features/scheduled-stories.md`: `content_kind=story`, story-connect VK, адаптеры `publishStory`/`deleteStory`, TTL/ссылки, batch, publications через тот же конвейер.
- **Приёмка:** TC-STORY-* P0; 0 дублей; TG/Max/IG stories не публикуются (явный отказ).

### BE-17. Граббер + RSS Ingest (E17)
- **Ветка:** `feat/be-17-grabber-ingest` · **Зависит от:** BE-9, BE-10, apps/ingest, **AN-P**.
- **Сделать:** по `docs/product/features/grabber.md` v1.0.1+: `automation_rules`, runs/items, дедуп, атрибуция, RSS; **`publish_mode=moderate` по умолчанию и как обязательный режим продукта**, `auto` — опционально; очередь модерации (approve/reject/edit-and-approve); публикация только через конвейер. **BE-17b** (отдельный PR): HTML-сайты + SSRF.
- **Приёмка:** TC-GRAB-* P0 (в т.ч. default moderate без публикации до approve); повторный ingest не создаёт дублей публикаций.

---

## Posts Разраб Фронт

### FE-1. Каркас приложения и дизайн-система
- **Ветка:** `feat/fe-1-app-shell` · **Зависит от:** DEV-1.
- **Сделать:** дизайн-токены (светлая/тёмная тема), базовые компоненты в `frontend/packages/ui` (Button, Input, Select, Dialog, Sheet, Tabs, Toast, Tooltip, Badge статусов, Avatar, NetworkIcon, Skeleton, EmptyState), Storybook с a11y-аддоном, layout приложения (шапка, боковое меню, переключатель workspace — заглушки), i18n (next-intl, ru), TanStack Query provider, обработка ошибок.
- **Приёмка:** Storybook собирается в CI; Lighthouse a11y ≥ 90 на layout; тёмная тема работает.

### FE-2. Публичные страницы и auth-экраны (на моках)
- **Ветка:** `feat/fe-2-auth-screens` · **Зависит от:** FE-1.
- **Сделать:** лендинг-заглушка, тарифы, вход/регистрация/подтверждение email/восстановление пароля/шаг 2FA, кнопки VK ID и Яндекс ID; формы на react-hook-form + zod; MSW-моки.
- **Приёмка:** экраны адаптивны (360–1920 px), компонентные тесты Vitest, e2e-заготовка Playwright для регистрации (с QA).

### FE-3. Подключение реального TS-клиента и MSW по контракту
- **Ветка:** `feat/fe-3-api-client` · **Зависит от:** BE-3.
- **Сделать:** перегенерировать `packages/api-client` из `docs/api/openapi.yaml`, обёртка (базовый URL, refresh-токен по cookie, обработка ProblemDetail), фабрика query-ключей, MSW-хендлеры, типизированные по схеме; подключить auth-экраны к клиенту.
- **Приёмка:** `pnpm gen:api` воспроизводим, CI `contract` зелёный; типовые ошибки API отображаются корректно.

### FE-4. Прототип редактора поста с предпросмотром
- **Ветка:** `feat/fe-4-editor-prototype` · **Зависит от:** FE-3.
- **Сделать:** Tiptap-редактор, выбор каналов (моки), вкладки вариантов по сетям, предпросмотр VK/TG/OK/Max, конвертер в `packages/domain`, прогон эталонных фикстур `docs/api/fixtures/formatting`, валидация по `GET /networks/capabilities`, автосохранение в IndexedDB.
- **Приёмка:** все фикстуры форматирования проходят в Vitest; редактор работает с клавиатуры; INP ≤ 200 мс на тексте 4 000 символов.

Далее (в порядке `03-frontend.md` §6): FE-5 workspace/команда · FE-6 каналы и мастера подключения · FE-7 медиа · FE-8 календарь + список + WS-статусы (STOMP) · FE-9 уведомления/настройки/дашборд/онбординг · FE-10 биллинг и админ-панель · FE-11 производительность, a11y, PWA.

### FE-12. Паритет редактора поста + UI маркировки ОРД
- **Ветка:** `feat/fe-12-ord-ad-editor` · **Зависит от:** FE-4, FE-8, BE-15, **AN-P**.
- **Сделать:** по `docs/product/features/scheduled-posts-and-ad-marking.md` — полные опции поста, экран настроек ОРД, контрагенты, предпросмотр «Реклама»+erid, валидация ИНН/ККТУ.
- **Приёмка:** e2e TC-AD-01…04 на моках/MSW; a11y редактора без регрессий.

### FE-13. UI отложенных историй
- **Ветка:** `feat/fe-13-scheduled-stories` · **Зависит от:** FE-7, FE-12, BE-16, **AN-P**.
- **Сделать:** по `docs/product/features/scheduled-stories.md` v1.0.1+ — создать историю, список/фильтр календаря, VK story-connect, batch, TTL/ссылки, ads. Instagram не показывать в UI (Q-STORY-05).
- **Приёмка:** e2e TC-STORY P0 на моках; сети TG/Max скрыты с пояснением; IG отсутствует в селекторе.

### FE-14. UI граббера / RSS
- **Ветка:** `feat/fe-14-grabber` · **Зависит от:** FE-6, BE-17, **AN-P**.
- **Сделать:** по `docs/product/features/grabber.md` v1.0.1+ — правила, активные, ручной run, история, **очередь модерации как default UX** (`moderate`; переключение на `auto` — опционально с предупреждением), юр. чекбокс.
- **Приёмка:** e2e TC-GRAB P0 на моках (default moderate; approve path).

---

## Posts Аналитик

### AN-1. PRD MVP, глоссарий, роли
- **Ветка:** `docs/an-1-prd-mvp` · **Зависит от:** DEV-1.
- **Сделать:** `docs/product/PRD-MVP.md`, `glossary.md`, `roles-permissions.md` (матрица роль × действие в машиночитаемом виде — таблица/YAML для автогенерации RBAC-тестов) по `01-analyst.md`.
- **Приёмка:** утверждено владельцем (гейт A); Бэк и QA подтвердили, что матрица пригодна для автотестов.

### AN-2. Матрица возможностей сетей
- **Ветка:** `docs/an-2-network-capabilities` · **Зависит от:** AN-1.
- **Сделать:** `docs/product/network-capabilities.md` + `docs/api/fixtures/network-capabilities.yaml` (лимиты текста/медиа, форматы, форматирование, закреп, удаление, первый комментарий, истории, лимиты API, токены) для VK, Telegram, OK, Max, Rutube — каждое значение со ссылкой на официальную документацию и датой проверки.
- **Приёмка:** Бэк использует YAML для `NetworkCapabilities` и конфигурации лимитов; нет значений без источника.

### AN-3. User stories E1–E6 с критериями приёмки
- **Ветка:** `docs/an-3-stories-mvp` · **Зависит от:** AN-1.
- **Сделать:** `docs/product/stories/E1…E6-*.md` (Given/When/Then, приоритет, зависимости), GitHub Issues по stories с метками (скрипт или вручную владельцем), `billing-rules.md`.
- **Приёмка:** каждая P0 story соответствует Definition of Ready (`01-analyst.md` §10); QA подтвердил тестируемость.

### AN-4. Ревью контракта API
- **Ветка:** — (ревью PR BE-3) · **Зависит от:** BE-3.
- **Сделать:** проверить, что `openapi.yaml` покрывает stories E1–E6; замечания — комментариями в PR.

### AN-P. Матрица паритета vposter и детальные спецификации ключевых инструментов
- **Ветка:** `feat/an-p-vposter-parity` · **Зависит от:** AN-1, AN-2, AN-3. **Блокирует:** BE-15…17, FE-12…14, QA-10…12.
- **Сделать:**
  1. Исследование vposter (help, API v3, блог, тарифы) + API сетей + ФЗ-38/ОРД — со ссылками.
  2. `docs/product/parity-matrix.md` — все функции оригинала.
  3. Спеки: `features/scheduled-posts-and-ad-marking.md`, `scheduled-stories.md`, `grabber.md` (UI, сети, модель, OpenAPI, надёжность, RBAC, юр., AC, TC).
  4. `parity-open-questions.md` + допущения.
  5. Обновить `02-backend.md`, `03-frontend.md`, `05-qa.md`, `agent-tasks.md` (только дополнения; не ломать BE-1…7, FE-1…6, QA-2…6).
- **Приёмка:** матрица полная; три спеки согласованы с конвейером/адаптерами/RBAC; план задач обновлён; PR с резюме пробелов и топ-рисков.

### AN-5. User stories Фазы 2 (E8–E22) по итогам AN-P
- **Ветка:** `docs/an-5-phase2-stories` · **Зависит от:** AN-P.
- **Сделать:** stories E10/E11/E17 (и связанные) в `docs/product/stories/` по спекам AN-P; черновик `PRD-phase2.md`.
- **Приёмка:** DoR для P0 stories паритета; Issues (владельцем).

---

## Posts Тестировщик

### QA-1. Тест-стратегия и шаблоны
- **Ветка:** `docs/qa-1-strategy` · **Зависит от:** DEV-1.
- **Сделать:** `docs/qa/test-strategy.md` (по `05-qa.md`), шаблоны тест-кейса и баг-репорта, классификация дефектов, критерии входа/выхода.
- **Приёмка:** согласовано с Аналитиком и Бэком.

### QA-2. Каркас e2e, контрактных и нагрузочных тестов
- **Ветка:** `feat/qa-2-test-harness` · **Зависит от:** DEV-1 (Playwright/k6), BE-3 (Schemathesis).
- **Сделать:** `tests/e2e` (Playwright + TypeScript, конфиг для локали/CI, page objects, smoke `/health` веба и `/api/v1/ping`), `tests/load` (k6: скрипт smoke + каркас сценариев L1/L2), workflow `nightly.yml` (Schemathesis против поднятого API из docker-compose, e2e, отчёты-артефакты).
- **Приёмка:** локально и в CI проходят smoke-тесты; отчёты сохраняются как артефакты.

### QA-3. Эмулятор соцсетей
- **Ветка:** `feat/qa-3-social-emulator` · **Зависит от:** DEV-1. **Блокирует:** BE-4 (полноценно), DEV-4.
- **Сделать:** `tests/social-emulator` — лёгкое Spring Boot-приложение (Java 21) с эндпоинтами, имитирующими VK API (`wall.post` с `guid`, загрузка фото, ошибки 6/9/10/15/100), Telegram Bot API (`sendMessage`, `sendPhoto`, `sendMediaGroup`, 429 + `retry_after`), OK и Max (базовая публикация); управляющий API для сценариев (задержки, доля ошибок, «успех без ответа»/обрыв соединения), счётчики созданных постов и API сверки дублей; Docker-образ (Jib) и сервис в docker-compose.
- **Приёмка:** сценарии ошибок включаются через управляющий API; есть README и тесты эмулятора; бэк-прототип BE-4 работает против него.

### QA-4. Тест-план MVP, тестовые данные, первые кейсы
- **Ветка:** `docs/qa-4-test-plan` · **Зависит от:** AN-3.
- **Сделать:** `docs/qa/test-plan-mvp.md`, `docs/qa/test-cases/E1…E5.md` (TC-AUTH, TC-RBAC, TC-CH, TC-POST, TC-PUB из `05-qa.md`), seed-сценарий тестовых данных (пользователи всех ролей, workspace с 1 000 каналов, 50 000 постов), пополнение фикстур форматирования (≥ 50 кейсов: вложенные теги, спецсимволы, эмодзи, ссылки, лимиты).
- **Приёмка:** каждый P0 AC покрыт хотя бы одним кейсом; кейсы помечены «авто/ручной».

Далее: QA-5 автотесты конвейера TC-PUB-* (с BE-4/BE-10) · QA-6 RBAC-матрица автоматически из `roles-permissions` · QA-7 нагрузка L1–L6 на staging · QA-8 хаос/DR с Devops · QA-9 security-чеклист и release-report.

### QA-10. Автотесты маркировки / ОРД (E10)
- **Ветка:** `feat/qa-10-ord-ad-tests` · **Зависит от:** BE-15, FE-12, **AN-P** (кейсы `E10-ad-marking.md` уже в репо).
- **Сделать:** автоматизировать TC-AD-*; WireMock ORD; fail-closed; идемпотентность креатива; регресс TC-PUB на рекламных постах.
- **Приёмка:** P0 TC-AD авто в CI/nightly.

### QA-11. Автотесты отложенных историй (E11)
- **Ветка:** `feat/qa-11-stories-tests` · **Зависит от:** BE-16, FE-13, QA-3 (расширение эмулятора), **AN-P**.
- **Сделать:** TC-STORY-*; эмулятор VK stories / OK Moments; 0 дублей.
- **Приёмка:** P0 TC-STORY авто зелёные.

### QA-12. Автотесты граббера / RSS (E17)
- **Ветка:** `feat/qa-12-grabber-tests` · **Зависит от:** BE-17, FE-14, **AN-P**.
- **Сделать:** TC-GRAB-* (в т.ч. default `moderate`, опциональный `auto`); дедуп; SSRF; очередь модерации; связь с конвейером.
- **Приёмка:** P0 TC-GRAB авто зелёные.

---

## Сводный порядок запуска агентов
| Шаг | Параллельно запускаются | Условие старта |
|---|---|---|
| 1 | DEV-1 | репозиторий создан, агенты подключены |
| 2 | DEV-2, DEV-3, BE-1, FE-1, AN-1, QA-1, QA-3 | DEV-1 смёржен |
| 3 | BE-2, FE-2, AN-2, AN-3 | BE-1 / FE-1 / AN-1 смёржены |
| 4 | BE-3 → (ревью AN-4, FE, QA) | BE-2 смёржен |
| 5 | BE-4, BE-5, FE-3, FE-4, QA-2, QA-4, DEV-4 | BE-3 смёржен (гейт C), QA-3 готов |
| 6 | BE-6…14, FE-5…11, DEV-5, DEV-6, QA-5…9 | по спринтам `00-overview.md` §6 |
| 7 | **AN-P** (паритет vposter) | AN-1…3 смёржены; можно параллельно шагу 6 |
| 8 | BE-15 + FE-12 + QA-10 (ОРД) | AN-P + BE-9/10 |
| 9 | BE-16 + FE-13 + QA-11 (истории) | BE-15 (ads), BE-7/8 |
| 10 | BE-17 + FE-14 + QA-12 (граббер) | BE-9/10; параллельно шагу 9 после AN-P |
