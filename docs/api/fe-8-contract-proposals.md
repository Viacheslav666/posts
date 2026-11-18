# FE-8: предложения по контракту OpenAPI

Задача FE-8 реализована на существующем `docs/api/openapi.yaml` без breaking changes.
Ниже — расширения, которые MSW уже поддерживает, и решения BE-9 по каждому пункту.

## 1. Фильтр автора в списке постов — **принято**

`GET /api/v1/posts` принимает optional `authorId` (uuid), как `GET /api/v1/calendar`.

## 2. Сортировка списка постов — **принято**

Optional `sort` enum:
`scheduledAt_asc | scheduledAt_desc | title_asc | updatedAt_desc`
(default: `scheduledAt_desc`).

## 3. Bulk duplicate — **принято**

`BulkPostsRequest.action` расширен значением `duplicate` (без отдельного
`POST /api/v1/posts/bulk-duplicate`).

## 4. Хронология попыток публикации — **принято**

Добавлен `GET /api/v1/publications/{publicationId}/attempts` → `PublicationAttempt[]`
(`operationId: publicationsListAttempts`).

## 5. Слоты-«призраки» контент-плана — **отклонено (BE-9)**

Фаза 2 (рубрики / `content_slots`). Календарь UI может держать локальный MSW-fallback.
В BE-9 не добавляем `kind: post | slot` в `CalendarItem`, чтобы не тащить модель слотов
до появления таблиц рубрик. Вернёмся в задачах Фазы 2 / E9.

## 6. Маркировка рекламы на карточке — **частично принято**

Добавлен lightweight `AdMarkingSummary` (`enabled`, `erid`, `advertiserName`) на `Post`
и `CalendarItem` при `ad=true`. Полный блок ОРД / `ads_items` — BE-15 / FE-12.
