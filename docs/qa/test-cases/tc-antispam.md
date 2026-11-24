# P2 Antispam / BE-35 (TC-AS)

| | |
|---|---|
| Задача | QA-14 |
| Эпик | BE-35 - антиспам VK / Telegram / Max |
| Источник AC | p2 BE-35 «Приёмка» |
| Порядок SPI | order **10**, `CONSUMED` блокирует предложку (BE-34) |

## Трассировка AC → TC

| AC (BE-35 Приёмка) | Кратко | TC | Приоритет | Автоматизация |
|---|---|---|---|---|
| VK: reply → delete → ban 7д | Цепочка remote actions | TC-AS-01 | P0 | авто |
| Восстановление VK | `restored` в истории | TC-AS-02 | P0 | авто |
| Dry-run | Только `flagged` | TC-AS-03 | P0 | авто |
| Админ не тронут | Исключения | TC-AS-04 | P0 | авто |
| Под рекламной записью | Фильтр ads | TC-AS-05 | P0 | авто |
| Антиповтор 12 ч | 2-й и 3-й удалены | TC-AS-06 | P0 | авто |
| TG: ссылка удалена | deleteMessage | TC-AS-07 | P0 | авто |
| TG: `is_automatic_forward` skip | 0 действий | TC-AS-08 | P0 | авто |
| TG: &gt;48 ч / restore 422 | Окно / unsupported | TC-AS-09 | P0 | авто |
| Max: delete, `restorable=false` | История | TC-AS-10 | P0 | авто |
| Повтор события ×3 | 1 действие | TC-AS-11 | P0 | авто |
| Нормализация обхода | сп@м, cпам, с-п-а-м | TC-AS-12 | P0 | unit |
| Unit движка ≥40 | `AntispamEngineTest` | TC-AS-13 | P0 | авто |
| Нет прав VK → 422 | activate rule | TC-AS-14 | P0 | авто |
| IDOR / RBAC | 404 / 403 | TC-AS-15 | P0 | авто |
| Нагрузка 3000/s | p99 вердикта &lt; 5 мс | TC-AS-16 | P1 | load / nightly |

---

## TC-AS-01 - VK: стоп-слово → ответ с причиной, удаление, бан 7 дней

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | VK |

### Ожидание
Порядок remote actions: `reply_comment` **до** `delete_comment` **до** `ban_user`; `antispam_log.state=done`; бан 7 дней в эмуляторе.

---

## TC-AS-02 - VK: восстановление из истории → `restored`

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **частично**: `Be33RemoteActionsVkIT` (restore action). Класса `Qa14AntispamRestoreIT` нет |

### Ожидание
`POST …/antispam/log/{id}/restore` → `restore_comment`; эмулятор комментарий виден; log `state=restored`.

---

## TC-AS-03 - Dry-run: только `flagged`, удалений 0

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`antispam_rules.dry_run=true` → `action=flagged`; `social_remote_actions` пусто; `Be33RemoteActionsVkIT` не вызывается для delete.

---

## TC-AS-04 - Исключение: админ сообщества со стоп-словом не удаляется

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Автор в whitelist / admin → verdict `no_match` или skip; log без delete.

---

## TC-AS-05 - Комментарий под рекламной записью только при включённом фильтре

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`under_ads=false` → комментарий остаётся; `under_ads=true` + ads post → удаление.

---

## TC-AS-06 - Антиповтор: 3 одинаковых комментария автора за 12 ч

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Первый проходит (или не матчится); 2-й и 3-й с `reason repeat` → delete; **1** строка log на уникальный `remote_comment_key` (идемпотентность).

---

## TC-AS-07 - Telegram: ссылка в группе обсуждений → удалено

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | TG |

### Ожидание
`Be33RemoteActionsTgIT` / `AntispamProcessorIT`: `deleteMessage` вызван; linked discussion chat.

---

## TC-AS-08 - TG: `is_automatic_forward=true` не трогаем

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Копия поста канала в группе → **0** remote actions.

---

## TC-AS-09 - TG: сообщение старше 48 ч → `failed` / restore API → 422

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Код `tg_delete_window_expired`; `POST …/restore` → **422** `antispam_restore_unsupported`.

---

## TC-AS-10 - Max: `comment_created` + стоп-слово → delete; `restorable=false`

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | Max |

### Ожидание
`Be33RemoteActionsMaxIT` delete path; log `restorable=false`.

---

## TC-AS-11 - Идемпотентность: повтор inbound ×3 → одно антиспам-действие

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Ключ `antispam:{channelId}:{section}:{remoteCommentKey}:{action}`; одна запись `antispam_log`; одно удаление в сети.

---

## TC-AS-12 - Нормализация текста: обходы фильтра

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| Уровень | unit |
| **Автоматизация** | **авто** |

### Ожидание
`TextNormalizer` / engine cases: `сп@м`, латинская `c`, разделители `с-п-а-м` → match.

---

## TC-AS-13 - Unit AntispamEngine (≥40 кейсов)

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`AntispamEngineTest` + `AntispamActionOrderTest` зелёные в CI.

---

## TC-AS-14 - Нет прав VK wall → правило не активируется (422)

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **нет автотеста** |

### Ожидание
Activate без user token / scope → **422** с понятным кодом; rule остаётся `draft`.

---

## TC-AS-15 - IDOR / RBAC на эндпоинтах antispam

| Поле | Значение |
|---|---|
| Story / AC | BE-35 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`RbacEndpointMatrixIT` / `RbacIdorMatrixIT` для `/api/v1/antispam/*`; INV-RBAC-007.

---

## Чеклист качества набора

- [x] VK / TG / Max, restore, dry-run, ban покрыты P0
- [x] CONSUMED vs suggest зафиксирован в трассировке BE-35 handler order

## История изменений

| Дата | Изменение |
|---|---|
| 2026-11-19 | Первичный набор TC-AS (QA-14) |