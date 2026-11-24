# P2 Inbound / BE-32 (TC-IN)

| | |
|---|---|
| Задача | QA-14 |
| Эпик | BE-32 - входящие события VK Callback / TG webhook / Max webhook |
| Спека | ADR-0013; `docs/runbooks/social-inbound.md`; p2 BE-32 «Приёмка» |
| Источник AC | p2 spec **BE-32** §«Приёмка» (agent prompt `BE-32.txt`) |
| Инварианты | **0 потерь / 0 дублей** на пути хук → очередь → handler; p99 хука &lt; 200 мс под нагрузкой |

## Трассировка AC → TC

| AC (BE-32 Приёмка) | Кратко | TC | Приоритет | Автоматизация |
|---|---|---|---|---|
| VK ensure + confirmation | Callback-сервер зарегистрирован | TC-IN-01 | P0 | авто |
| Дедуп Callback ×3 | Один вызов handler | TC-IN-02 | P0 | авто |
| Неверный secret | 401, не в очереди | TC-IN-03 | P0 | авто |
| Альбом TG 3 фото | 1 событие, 3 вложения | TC-IN-04 | P0 | авто |
| Fan-out TG | 2 workspace → 2 события | TC-IN-05 | P0 | авто |
| Max secret + comment | Нормализация, 401 при плохом секрете | TC-IN-06, TC-IN-07 | P0 | авто |
| Chaos RabbitMQ | 503, после восстановления 0 потерь | TC-IN-08 | P0 | авто / nightly |
| Нагрузка 2000/s | p99 &lt; 200 мс, 0 потерь | TC-IN-09 | P0 | load / nightly |
| Секреты / `/_control` | Нет хардкода; control только emulator | TC-IN-10 | P0 | авто |
| IDOR подписки | Чужой канал → 404 | TC-IN-11 | P0 | авто |
| OpenAPI / RBAC / squawk | Регрессия артефактов | TC-IN-12 | P0 | CI |

---

## TC-IN-01 - VK: ensure регистрирует Callback; confirmation отдаёт сохранённый код

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | VK (эмулятор) |

### Предусловия
- Канал VK в workspace Editor; `EventSubscriptionService.ensure(..., suggest|crosspost|antispam)`.

### Ожидание
Эмулятор: `groups.addCallbackServer` → `confirmation` на `/hooks/vk/{channelId}` возвращает `text/plain` код из подписки; `GET …/event-subscription` → `status=active`.

---

## TC-IN-02 - Дедуп VK Callback по `event_id` (повтор ×3)

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Эмулятор шлёт один `wall_post_new` с тем же `event_id` трижды → в `social_inbound_events` одна строка; тестовый `RecordingSocialEventHandler` / счётчик handler - **ровно 1** обработка; **0** лишних публикаций downstream.

---

## TC-IN-03 - Неверный VK `secret` → 401, сообщение не в RabbitMQ

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| Тип | негативный / безопасность |
| **Автоматизация** | **авто** |

### Ожидание
`POST /hooks/vk/{channelId}` с чужим `secret` → **401**; depth `ingest.vk` не растёт; метрика `social_hook_auth_failures_total` инкремент.

---

## TC-IN-04 - Telegram: альбом `media_group_id` (3 фото) → одно `POST_NEW`

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Уровень | integration (`TelegramAlbumAggregator`, Redis 3 с) |

### Ожидание
Три `channel_post` с одним `media_group_id` → после drain очереди одно нормализованное событие с `attachments.length=3`.

---

## TC-IN-05 - Один TG `chat.id` в двух workspace → fan-out по каналам

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Два канала с одним `external chat id` → два `SocialInboundEvent` с разными `workspaceId` / `channelId`; **без** смешивания данных.

---

## TC-IN-06 - Max: подписка с секретом; `comment_created` нормализуется

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | Max (эмулятор) |

### Ожидание
`POST /_control/max/update` с валидным `X-Max-Bot-Api-Secret` → 200 `ok`; ingest → `kind=COMMENT_NEW`; текст и id комментария на месте.

---

## TC-IN-07 - Max: неверный webhook secret → 401

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| Тип | негативный |
| **Автоматизация** | **авто** |

### Ожидание
Хук без / с неверным секретом → **401**; событий в БД нет.

---

## TC-IN-08 - Chaos: RabbitMQ недоступен → 503; после восстановления 0 потерь

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| Тип | хаос |
| **Автоматизация** | **авто** (`@Tag("nightly")`) |
| Док | `tests/chaos/tc-p2-automations-chaos.md` |

### Ожидание
При остановке брокера хук → **503** + `Retry-After`; эмулятор VK повторяет доставку; после `posts-ingest` online все события обработаны; **потерь 0**, дедуп сохранён.

---

## TC-IN-09 - Нагрузка: 2000 webhook/s, p99 &lt; 200 мс

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| Уровень | load |
| **Автоматизация** | **авто** (k6 nightly `PROFILE=full`) |

### Ожидание
`tests/load/scenarios/l3-social-inbound-hooks.js`: `INBOUND_RATE=2000`, счётчик `social_inbound_hook_lost==0`, `http_req_duration p(99)<200`.

---

## TC-IN-10 - Нет хардкода секретов; `/_control` только в emulator-профиле

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| Тип | безопасность / регрессия |
| **Автоматизация** | **авто** |

### Ожидание
Статический аудит / `ProdProfileNoControlPathTest`; прод YAML без `/_control`; live social guard - `SocialLiveApiGuardTest`.

---

## TC-IN-11 - IDOR: чужой `channelId` на event-subscription → 404

| Поле | Значение |
|---|---|
| Story / AC | BE-32 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`GET/POST …/channels/{foreign}/event-subscription*` → **404** `not_found` (не 403).

---

## TC-IN-12 - RBAC map / OpenAPI / squawk (регрессия BE-32 API)

| Поле | Значение |
|---|---|
| Story / AC | BE-32 DoD |
| Приоритет | P0 |
| **Автоматизация** | **авто** (CI) |

### Ожидание
`RbacMatrixDocsSyncTest` зелёный; `event-subscription` в `rbac-endpoint-map.yaml`; squawk на V26 без блокеров.

---

## Чеклист качества набора

- [x] Все пункты «Приёмка» BE-32 покрыты ≥ 1 TC P0
- [x] Явно **0 потерь / 0 дублей** для ingest
- [x] Chaos и load вынесены в nightly/full profile

## История изменений

| Дата | Изменение |
|---|---|
| 2026-11-19 | Первичный набор TC-IN (QA-14) |