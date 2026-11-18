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

`OrdPrePublishHook` (`PrePublishHook`): при `is_ad` без erid → `PERMANENT` (`ord_erid_missing`), публикация в сеть не уходит (**fail-closed**). Feature-flag `posts.ord.allow-publish-without-erid` по умолчанию **false**. При наличии erid — префикс `AdMarker` («Реклама. … erid: …»). Регистрация креатива идемпотентна по `external_id = creative:{postId}:{version}` (на schedule / `POST .../ord/register`).

### Отчётность

- Ручной `POST /api/v1/ord/reports/run` (Owner/Admin).
- ShedLock-job scheduler: cron `0 0 3 7 * *` Europe/Moscow → idempotent `ord_report_runs` за предыдущий месяц.
- Автоудаление после `display_period_days` (default 30) через `scheduled_actions` delete.

### Схема

Flyway `V11__ord_be15.sql`: `ord_connections`, `ord_contractors`, `ord_contracts`, `ord_creatives`, `ord_platforms`, `ord_invoices`, `ord_statistics`, словари ККТУ/ОКСМ, `ord_report_runs`. Expand-only; CHECK через `NOT VALID` + `VALIDATE`.

## Альтернативы

- **Stub-адаптеры до «реальной» интеграции** — отвергнуто (Q-ORD-03).
- **Субсидирование ОРД Posts** — отвергнуто (Q-ORD-06).
- **Публикация рекламы без erid с постфактум-регистрацией** — отвергнуто (fail-closed / ФЗ-38).

## Последствия

- OpenAPI + RBAC (`ord.*`, `posts.ad.schedule`); WireMock-тесты адаптеров и TC-AD-*.
- Resilience/метрики: ошибки ORD классифицируются как у конвейера (retryable vs permanent).
- Расширение на ОЗОН — новый модуль `ord-ozon` без смены UX/`OrdProvider`.
