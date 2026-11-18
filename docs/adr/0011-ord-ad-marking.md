# ADR-0011: ОРД и маркировка рекламы (BE-15)

- Статус: accepted
- Дата: 2026-11-18
- Авторы: Posts Разраб Бэк (BE-15)
- Связано: ADR-0004, ADR-0006, ADR-0010, `02-backend.md` §10 / §12.1, `features/scheduled-posts-and-ad-marking.md` v1.0.1

## Контекст

Паритет vposter «Отложенные посты» требует маркировки интернет-рекламы (ФЗ-38 ст. 18.1): erid, пометка «Реклама», данные рекламодателя, передача креативов/актов/статистики в ОРД. Заказчик закрыл Q-ORD-03/06: полноценные адаптеры **VK ОРД** и **ОРД Яндекс**; ключ клиента; Posts не субсидирует ОРД. ОЗОН и др. — позже через тот же порт.

## Решение

### Порт `OrdProvider` (`libs/ord-api`)

Единый интерфейс: `testConnection`, `upsertPerson`, `upsertContract`, `registerCreative` → erid, `submitInvoice`, `submitStatistics`, `classifyError`. Провайдеры: `vk_ord`, `yandex_ord` (`ozon_ord` зарезервирован в схеме).

### Адаптеры (не stub)

| Провайдер | Модуль | Base URL (default) | Auth | Ключевые эндпоинты |
|---|---|---|---|---|
| VK ОРД | `libs/ord-vk` | `https://api-sandbox.ord.vk.com` (prod: `https://api.ord.vk.com`) | `Authorization: Bearer {apiKey}` | `GET/PUT /v1/person/{id}`, `PUT /v1/contract/{id}`, `PUT /v3/creative/{id}` → `erid`, `PUT /v1/invoice/{id}`, `POST /v1/statistics` |
| ОРД Яндекс | `libs/ord-yandex` | `https://ord-prestable.yandex.net` (prod: `https://ord.yandex.net`) | Bearer token | `POST /api/v8/organization\|contract\|creative\|invoice\|statistics`; erid = `token` в ответе creative |

Источники (проверено 2026-11-18):

- https://ord.vk.ru/help/api/api
- https://ord.vk.ru/help/api/ref/creative
- https://ord.vk.ru/help/api/ref/person
- https://ord.vk.ru/help/api/ref/contract
- https://yandex.ru/support/ord/ru/api/connect
- https://ord-prestable.yandex.net/api/docs (OpenAPI v8)

### Ключ и шифрование

Клиентский API-ключ хранится в `ord_connections.api_key_enc` через тот же envelope (`TokenCryptoService` + workspace DEK, ADR-0006 / `WorkspaceTokenVault`). Plaintext только в памяти на время вызова; API и логи ключ не отдают.

### Конвейер (хук BE-10)

`OrdPrePublishHook` (`PrePublishHook`): при `is_ad` без erid → `PERMANENT` (`ord_erid_missing`), публикация в сеть не уходит (**fail-closed**). Feature-flag `posts.ord.allow-publish-without-erid` по умолчанию **false**. При наличии erid — `AdMarker` применяет один из 6 режимов размещения (BE-25 / Q-ORD-05): `text_start|text_end|click_link_*|vk_menu*`. VK API `wall.post` поддерживает только `mark_as_ads=1` (без меню advertiser+erid) → `vk_menu*` честно падает в `text_end` + meta/`warnings` `vk_menu_unsupported`. Регистрация креатива идемпотентна по `external_id = creative:{postId}:{version}` (на schedule / `POST .../ord/register`, право `ord.register`).

### Отчётность (BE-26)

- Ручной `POST /api/v1/ord/reports/run` (Owner/Admin) и ежемесячный ShedLock-job (`0 0 3 7 * *` Europe/Moscow) вызывают **один** сервис `OrdReportRunner` (`libs/ord-reporting`): креативы, видимые в периоде → показы VK/OK/stories → `ord_invoices` / `ord_statistics` → `submitInvoice` / `submitStatistics` с ретраями (`attempts` / `next_retry_at`). Повторный запуск при `succeeded` не дублирует.
- Площадки: при первом рекламном посте / регистрации креатива — идемпотентный upsert в `ord_platforms` + ORD pad (`external_id = platform:{network}:{channelExternalId}`) для VK ОРД и Яндекс ОРД.
- API отчётности: `GET /workspaces/{id}/ord/reports`, `GET /ord/creatives`, `POST /ord/erid/check`, `DELETE /ord/invoices/{id}` (soft-delete + audit), экспорт CSV/XLSX.
- Алерты: просрочка к 25-му (метрика `ord_report_overdue_total` + лог), ошибки ОРД → outbox `ord.problem` / уведомления; Prometheus `ord_report_*`.

### Срок жизни рекламы / статистика (BE-26)

- Статистика по креативу подаётся **каждый месяц, пока пост виден** (окно `display_started_at` … `display_ended_at`), а не только в месяц публикации; месяц снятия включается.
- Предупреждение владельцу за N дней до конца `display_period_days` / `contract.date_end` (свойство `posts.ord.reports.expiry-warning-days`, default **3**) — outbox `ord.expiry_warning`.
- После снятия поста (автоудаление `scheduled_actions` delete/delete_story или ручное удаление): фиксируется `display_ended_at`, `final_stats_pending=true`; ближайший отчёт подаёт финальную статистику. **Креатив и erid не удаляются** (хранятся для отчётности / ЕРИР).

### Схема

Flyway `V12__ord_be15.sql` (после V11 граббера BE-17): `ord_connections`, `ord_contractors`, `ord_contracts`, `ord_creatives`, `ord_platforms`, `ord_invoices`, `ord_statistics`, словари ККТУ/ОКСМ, `ord_report_runs`. Expand-only; CHECK через `NOT VALID` + `VALIDATE`.

BE-26: `V24__ord_be26_reporting.sql` — `ord_invoices.deleted_at`, `ord_statistics.views_source`, `ord_report_runs.attempts/next_retry_at`, поля окна показа на `ord_creatives`, индексы таблицы отчётности.

## Альтернативы

- **Stub-адаптеры до «реальной» интеграции** — отвергнуто (Q-ORD-03).
- **Субсидирование ОРД Posts** — отвергнуто (Q-ORD-06).
- **Публикация рекламы без erid с постфактум-регистрацией** — отвергнуто (fail-closed / ФЗ-38).

## Последствия

- OpenAPI + RBAC (`ord.*`, `posts.ad.schedule`); WireMock-тесты адаптеров и TC-AD-*.
- Resilience/метрики: ошибки ORD классифицируются как у конвейера (retryable vs permanent).
- Расширение на ОЗОН — новый модуль `ord-ozon` без смены UX/`OrdProvider`.
