# FE-8: предложения по контракту OpenAPI

Задача FE-8 реализована на существующем `docs/api/openapi.yaml` без breaking changes.
Ниже — расширения, которые MSW уже поддерживает и которые желательно добавить бэкендом (BE-9).

## 1. Фильтр автора в списке постов

`GET /api/v1/posts` сейчас не принимает `authorId` (есть только в `GET /api/v1/calendar`).
FE передаёт `authorId` как query-параметр; MSW фильтрует, реальный API до контракта может игнорировать.

**Предложение:** добавить optional `authorId` (uuid) в `postsList`.

## 2. Сортировка списка постов

**Предложение:** optional `sort` enum:
`scheduledAt_asc | scheduledAt_desc | title_asc | updatedAt_desc`
(сейчас сортировка — MSW-расширение / клиентский fallback).

## 3. Bulk duplicate

`BulkPostsRequest.action` = `pause | resume | delete | reschedule`.
UI «Дублировать» вызывает MSW `POST /api/v1/posts/bulk-duplicate` либо fallback на
последовательные `POST /api/v1/posts/{id}/duplicate`.

**Предложение:** либо `action: duplicate` в `postsBulk`, либо задокументированный
`POST /api/v1/posts/bulk-duplicate`.

## 4. Хронология попыток публикации

`Publication.attempts` — счётчик. Для UI «хронология попыток» нужен список
`PublicationAttempt[]` (время, результат, код ошибки).

**Предложение:** `GET /api/v1/publications/{id}/attempts` или вложенный массив в DTO.

## 5. Слоты-«призраки» контент-плана

Фаза 2 (рубрики/слоты). Календарь UI готов принимать `ghosts[]`, в контракте пока нет.

**Предложение:** optional `kind: post | slot` в `CalendarItem` + поля рубрики.

## 6. Маркировка рекламы на карточке

`AdMarkingSlot` читает `post.ad` и опциональный MSW-объект `adMarking: { enabled, erid, advertiserName }`.
Полные поля — в FE-12 / BE-15.
