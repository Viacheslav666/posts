# P2 VK Suggest / BE-34 (TC-SUG)

| | |
|---|---|
| Задача | QA-14 |
| Эпик | BE-34 — автопостинг предложенных записей VK |
| Story | [`docs/product/stories/E18-US-18.1-vk-suggests-autopost.md`](../../product/stories/E18-US-18.1-vk-suggests-autopost.md) |
| Источник AC | p2 BE-34 «Приёмка» |
| Пайплайн | Публикация через BE-33 `publish_suggested`; **0 дублей** на redelivery / `UNKNOWN_OUTCOME` |

## Трассировка AC → TC

| AC (BE-34 Приёмка) | Кратко | TC | Приоритет | Автоматизация |
|---|---|---|---|---|
| Авто: слот, signed, 1-й комментарий | Callback → wall | TC-SUG-01 | P0 | авто |
| Стоп-слово → reject + delete + notify | Фильтр + remote actions | TC-SUG-02 | P0 | авто |
| Лимит на автора / сутки | 3-я запись `filtered_out` | TC-SUG-03 | P0 | авто |
| Дедуп Callback ×3 | 1 item, 1 публикация | TC-SUG-04 | P0 | авто |
| Bulk `approve_all` | 500×, прогресс 100%, без дублей | TC-SUG-05 | P0 | авто |
| `UNKNOWN_OUTCOME` | 0 дублей | TC-SUG-06 | P0 | авто |
| Сверка `removed_externally` | wall.get suggests | TC-SUG-07 | P0 | авто |
| Пауза после рекламы | Слот сдвигается | TC-SUG-08 | P0 | авто |
| Антидубль SimHash 7 дней | `filtered_out` | TC-SUG-09 | P0 | авто |
| Фильтры unit ≥30 | ё/е, TZ, окна | TC-SUG-10 | P0 | авто |
| IDOR / RBAC | 404 / INV-RBAC-007 | TC-SUG-11 | P0 | авто |
| Нагрузка 200/s × 10k каналов | p95 решение &lt; 2 с | TC-SUG-12 | P1 | load / nightly |

---

## TC-SUG-01 — Режим `auto`: Callback `post_type=suggest` → публикация + `signed=1` + первый комментарий

| Поле | Значение |
|---|---|
| Story / AC | BE-34 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | VK (эмулятор) |

### Предусловия
- `suggest_settings.status=active`, `mode=auto`; user token с `wall`; `EventSubscriptionService.ensure(..., suggest)`.

### Ожидание
После ingest: `suggest_items` → `published`; эмулятор: `wall.post` с `post_id`, `signed=1`; `reply_comment` (первый комментарий); **1** пост на стене.

---

## TC-SUG-02 — Стоп-слово: `rejected` + `delete_post` + уведомление (если разрешены сообщения)

| Поле | Значение |
|---|---|
| Story / AC | BE-34 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Текст с exclude-keyword → `filtered_out` / `rejected` по политике; `social_remote_actions` delete; `messages.send` только при `isMessagesFromGroupAllowed=true`.

---

## TC-SUG-03 — Лимит на автора: третья запись за сутки → `filtered_out` с причиной

| Поле | Значение |
|---|---|
| Story / AC | BE-34 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Три разных `vk_post_id` одного `author_vk_id` в окне суток → третья с `reasons` содержит код лимита; публикаций **0** для третьей.

---

## TC-SUG-04 — Ручной / hybrid: `pending_moderation` до approve

| Поле | Значение |
|---|---|
| Story / AC | BE-34 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`mode=manual` (или hybrid + сомнительный фильтр) → item `pending_moderation`; **нет** `publish_suggested` до `POST …/approve`; approve → ровно 1 публикация.

---

## TC-SUG-05 — Bulk `approve_all`: N записей → N публикаций, прогресс, идемпотентность

| Поле | Значение |
|---|---|
| Story / AC | BE-34 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`suggest_bulk_jobs` доходит до 100%; повтор bulk с тем же `Idempotency-Key` — no-op; в эмуляторе **ровно N** wall posts (0 дублей).

---

## TC-SUG-06 — Дедуп Callback ×3 → одна запись и одна публикация

| Поле | Значение |
|---|---|
| Story / AC | BE-34 + BE-32 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Три одинаковых suggest callback → `unique(channel_id, vk_post_id)`; `publish_suggested` с одним `idempotencyKey`; эмулятор ≤ 1 публикация.

---

## TC-SUG-07 — Сверка: запись исчезла из VK suggests → `removed_externally`

| Поле | Значение |
|---|---|
| Story / AC | BE-34 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
После `wall.get(filter=suggests)` без id → статус `removed_externally`; повторная публикация **не** создаётся.

---

## TC-SUG-08 — Пауза после рекламного поста на стене

| Поле | Значение |
|---|---|
| Story / AC | BE-34 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Если последний пост стены `marked_as_ads` / erid — слот следующей suggest сдвигается за `ads_pause_minutes`; публикация не раньше окна.

---

## TC-SUG-09 — Антидубль: текст как у поста стены за 7 дней → `filtered_out`

| Поле | Значение |
|---|---|
| Story / AC | BE-34 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
SimHash near-duplicate к недавней публикации → `filtered_out`; **0** вызовов `publish_suggested`.

---

## TC-SUG-10 — `UNKNOWN_OUTCOME` на `publish_suggested` → сверка без второго поста

| Поле | Значение |
|---|---|
| Story / AC | BE-34 + BE-33 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Эмулятор / reconcile path: после «потерянного» ответа сети — `SuggestRemoteActionReconciler` / `Be33PublishSuggestedReconcileTest`; **0** дублей в эмуляторе.

---

## TC-SUG-11 — Unit-фильтры SuggestFilterEngine (≥30 кейсов)

| Поле | Значение |
|---|---|
| Story / AC | BE-34 |
| Приоритет | P0 |
| Уровень | unit |
| **Автоматизация** | **авто** |

### Ожидание
`SuggestFilterEngineTest`: ё/е, whole word, emoji, TZ `Europe/Moscow` / `Europe/Samara`, лимиты, dedup scope.

---

## TC-SUG-12 — IDOR и RBAC (Author/Viewer не модерируют)

| Поле | Значение |
|---|---|
| Story / AC | BE-34 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`SuggestApiIT`: чужой workspace → **404**; Author `approve` → **403**; Viewer `manage` → **403**.

---

## Чеклист качества набора

- [x] Темы QA-14: auto, manual, bulk, limits, ads pause, reconcile
- [x] **0 дублей** на triple callback и UNKNOWN_OUTCOME

## История изменений

| Дата | Изменение |
|---|---|
| 2026-11-19 | Первичный набор TC-SUG (QA-14) |