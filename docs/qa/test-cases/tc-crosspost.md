# P2 Crosspost / BE-36 (TC-XP)

| | |
|---|---|
| Задача | QA-14 |
| Эпик | BE-36 - кросспостинг VK / TG / Max |
| Story | [`docs/product/stories/E16-US-16.1-crosspost-rules.md`](../../product/stories/E16-US-16.1-crosspost-rules.md) |
| Источник AC | p2 BE-36 «Приёмка» |
| Петли | L1…L7 (`LoopGuard`); **0 обратных публикаций / 0 дублей** |

## Трассировка AC → TC

| AC (BE-36 Приёмка) | Кратко | TC | Приоритет | Автоматизация |
|---|---|---|---|---|
| VK → TG/OK/Max с медиа | 3 фото | TC-XP-01 | P0 | нет автотеста |
| TG альбом → VK 3 фото | Агрегация BE-32 | TC-XP-02 | P0 | частично (inbound) |
| Max → TG | Пара сетей | TC-XP-03 | P0 | нет автотеста |
| Длинный TG &gt;1024 + фото | Порядок сообщений | TC-XP-04 | P0 | нет автотеста |
| Петля A↔B | 1 копия в B, 0 в A | TC-XP-05 | P0 | частично (L1) |
| Гонка L2 (реестр) | intent без строки реестра | TC-XP-06 | P0 | авто |
| Треугольник A→B→C→A | 1 в B,C; 0 в A | TC-XP-07 | P0 | частично (L1, не L3) |
| L4 A→C и B→C | Одна копия в C | TC-XP-08 | P0 | авто |
| Повтор события ×5 | 1 публикация / приёмник | TC-XP-09 | P0 | авто |
| UNKNOWN_OUTCOME | 0 дублей | TC-XP-10 | P0 | нет автотеста |
| Автовыключатель L6 | `auto_paused` | TC-XP-11 | P0 | частично (unit) |
| Реклама erid skip | `ad_post_excluded` | TC-XP-12 | P0 | частично (unit) |
| TG файл &gt;20 МБ | `skipped_unsupported_media` | TC-XP-13 | P0 | авто |
| Свой пост / suggest option | L1 + config | TC-XP-14 | P0 | частично (unit) |
| Unit фильтры/трансформации ≥40 | libs/crosspost tests | TC-XP-15 | P0 | unit |
| IDOR / RBAC | 404 | TC-XP-16 | P0 | авто |
| Нагрузка 1000/мин ×3 | 0 петель, p95 &lt; 60 с | TC-XP-17 | P1 | нет замера |

---

## TC-XP-01 - VK пост (текст + 3 фото) → по 1 публикации в TG, OK, Max

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **нет автотеста** (класса `Qa14CrosspostPairsIT` нет) |
| Сети | VK → TG/OK/Max |

### Ожидание
`crosspost_items.status=published` (или partial с явной причиной); эмулятор: **1** remote post на приёмник; медиа 3; форматирование по `CrosspostTransformer`.

---

## TC-XP-02 - Telegram альбом (3 фото) → один VK wall с 3 вложениями

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **частично**: `SocialInboundIT#telegramAlbumAndFanOut` (inbound, не публикация VK) |

### Ожидание
После album aggregator - одна публикация VK; **не** три отдельных поста.

---

## TC-XP-03 - Пара Max → Telegram

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **нет автотеста** (класса `Qa14CrosspostPairsIT` нет) |

### Ожидание
Текст + markup переносятся; L7: пост от нашего бота в Max не источник (skip).

---

## TC-XP-04 - TG текст &gt;1024 с фото: медиа первым, остаток текста следом

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **нет автотеста** |

### Ожидание
Порядок публикаций в задании: photo+caption ≤1024, затем follow-up text; **0** потери хвоста.

---

## TC-XP-05 - Петля A↔B: 1 пост в A → ровно 1 в B, **0** обратно в A

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **Qa14CrosspostLoopsIT** |

### Ожидание
Активны правила A→B и B→A; метрики `crosspost_loop_blocked_total`; эмулятор: счётчики B+1, A без обратной копии.

---

## TC-XP-06 - L2: задержка коммита `remote_post_registry` (гонка)

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **Qa14CrosspostLoopsIT#registryLagBlockedByIntent** |

### Ожидание
`process_after` +15 с; `publish_intents` fingerprint; без ложного кросспоста «своего» поста.

---

## TC-XP-07 - Треугольник A→B→C→A

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **Qa14CrosspostLoopsIT** |

### Ожидание
По 1 копии в B и C; в A - **0** (L3 lineage / visited).

---

## TC-XP-08 - L4: B получает копию из A; A и B → C → одна копия в C

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **Qa14CrosspostLoopsIT#fanInOneCopyOnSharedTarget** + `LoopGuardAndGraphTest` |

### Ожидание
Второй кандидат → `skipped_duplicate`; SimHash ≤3.

---

## TC-XP-09 - Повтор Callback/update ×5 → одна публикация на приёмник

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **Qa14CrosspostLoopsIT#duplicateInboundFiveTimes** |

### Ожидание
Ключ `xp:{ruleId}:{sourceChannelId}:{sourceRemoteId}:{targetChannelId}`; `unique(rule_id, source_remote_id)`.

---

## TC-XP-10 - UNKNOWN_OUTCOME конвейера → 0 дублей в приёмнике

| Поле | Значение |
|---|---|
| Story / AC | BE-36 + BE-33 |
| Приоритет | P0 |
| **Автоматизация** | **нет для crosspost** (`Be33PublishSuggestedReconcileTest` - только suggest) |

### Ожидание
После reconcile в эмуляторе **1** пост на целевой канал.

---

## TC-XP-11 - L6: автопауза при всплеске / серии `skipped_loop`

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **частично**: unit `CircuitBreakerTest`, IT нет |

### Ожидание
`crosspost_rules.status=auto_paused`; уведомление owner; новые items не публикуются.

---

## TC-XP-12 - Рекламный пост (erid / «Реклама») → `ad_post_excluded`

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **частично**: unit `CrosspostFilterEngineTest`, IT нет |

### Ожидание
`crosspost_items.status=filtered_out`; publications **0**.

---

## TC-XP-13 - TG вложение &gt;20 МБ без Local Bot API → `skipped_unsupported_media`

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **Qa14CrosspostLoopsIT#telegramFileOver20MbSkipped** |

### Ожидание
Причина с кодом unsupported media; **0** upload в S3 / публикаций.

---

## TC-XP-14 - Свой пост из конвейера в A не переносится (default); suggest-published - по опции

| Поле | Значение |
|---|---|
| Story / AC | BE-36 L1 |
| Приоритет | P0 |
| **Автоматизация** | **частично**: unit `LoopGuardAndGraphTest`, IT нет |

### Ожидание
`include_posts_published_via_posts=false` → `skipped_own`; `include_suggest_published=true` → перенос suggest.

---

## TC-XP-15 - Unit: фильтры и трансформации (≥40)

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`CrosspostFilterEngineTest`, `CrosspostTransformerTest`, `CrosspostTextSignalsTest` в CI.

---

## TC-XP-16 - IDOR / RBAC crosspost API

| Поле | Значение |
|---|---|
| Story / AC | BE-36 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`CrosspostServiceIdorTest`; `CrosspostLiveApiGuardTest`; matrix IT для OpenAPI ops.

---

## Чеклист качества набора

- [x] Пары, петли A↔B, треугольник, L4 dedup, альбомы, &gt;20 МБ
- [x] Каждый loop-кейс - измеримый **0** обратных публикаций

## История изменений

| Дата | Изменение |
|---|---|
| 2026-11-19 | Первичный набор TC-XP (QA-14) |