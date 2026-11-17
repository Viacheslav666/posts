# E17 — Граббер / Ingest (TC-GRAB)

| | |
|---|---|
| Задача | QA-12 |
| Эпик | E17 — RSS-импорт и граббер (единый Ingest) |
| Спека | [`docs/product/features/grabber.md`](../../product/features/grabber.md) |
| Источник AC | grabber.md §13; parity-matrix G1–G9; `02-backend.md` §10 |
| Пайплайн | Публикации проверять как E5: **0 потерь / 0 дублей** |

## Трассировка AC → TC

| AC | Кратко | TC | Приоритет | Автоматизация |
|---|---|---|---|---|
| AC-G1 | Создать правило VK→VK, список активных | TC-GRAB-01 | P0 | авто |
| AC-G2 | Периодический скан + ручной run | TC-GRAB-02, TC-GRAB-03 | P0 | авто |
| AC-G3 | История items + permalink | TC-GRAB-04 | P0 | авто |
| AC-G4 | Дедуп remote + content_hash | TC-GRAB-05, TC-GRAB-06 | P0 | авто |
| AC-G5 | Attribution + responsibility | TC-GRAB-07 | P0 | авто |
| AC-G6 | Default `moderate` / approve; `auto` опционален | TC-GRAB-08, TC-GRAB-09, TC-GRAB-08b, TC-GRAB-11 | P0 | авто |
| AC-G7 | Пауза / resume | TC-GRAB-10 | P0 | авто |
| AC-G8 | RSS → пайплайн | TC-GRAB-11 | P0 | авто |
| AC-G9 | SSRF block | TC-GRAB-12 | P0 | авто |
| AC-G10 | Идемпотентность run / пайплайн E5 | TC-GRAB-13, TC-GRAB-14 | P0 | авто |
| AC-G11 | RBAC / IDOR | TC-GRAB-15, TC-GRAB-16 | P0 | авто |
| AC-G12 | Аудит | TC-GRAB-17 | P1 | авто |
| AC-G13 | Автопауза | TC-GRAB-18 | P1 | авто |
| фильтры | exclude ads/reposts, keywords | TC-GRAB-19 | P1 | авто |
| rate limit | лимит/час, ручной run 429 | TC-GRAB-20 | P1 | авто |
| AC-G14 | Website P1 | TC-GRAB-21 | P1 | кандидат в авто |
| нормализация | ID с пробелами / screen name | TC-GRAB-22 | P0 | авто |
| reject | модерация reject | TC-GRAB-23 | P0 | авто |
| bulk | массовый approve | TC-GRAB-24 | P1 | авто |
| validation | лимиты сети | TC-GRAB-25 | P0 | авто |
| channel reauth | auto_pause | TC-GRAB-26 | P1 | авто |
| edit-and-approve | правка перед публикацией | TC-GRAB-27 | P1 | авто |
| AC-G15 | AI rewrite (если флаг) | TC-GRAB-28 | P2 | ручной |

---

## TC-GRAB-01 — Создание правила grabber VK→VK и появление в активных

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G1 |
| Приоритет | P0 |
| Тип | функциональный |
| Уровень | integration / e2e |
| **Автоматизация** | **авто** |
| Сети | VK (эмулятор) |

### Предусловия
- Workspace + проект; VK-канал `ok`.
- Роль Editor (scope проекта).

### Ожидание
`POST .../ingest/rules` с `type=grabber`, `source_ids`, `targetChannelId`, `userResponsibilityAccepted=true`, attribution в config → 201; если `publish_mode` не передан — в config **`moderate`** (Q-GRAB-02); правило в `GET` списке со статусом `draft` или `active` (если `activate=true`); UI «Активные правила» показывает канал и источники.

---

## TC-GRAB-02 — Плановый скан по интервалу

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G2 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Данные | `scan_interval_minutes=15` (тест); эмулятор источника с 1 новым постом |

### Ожидание
После `next_run_at` создаётся `grabber_runs` со `trigger=scheduled`; новый `grabber_items`; `next_run_at` сдвинут на интервал; без лишних runs при двойном scheduler (аналог TC-PUB-08).

---

## TC-GRAB-03 — Ручной «Проверить сейчас»

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G2 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`POST .../rules/{id}/runs` → run `trigger=manual`; items обновлены; повтор чаще лимита → 429 `rate_limited` / `ingest_rate_limited`.

---

## TC-GRAB-04 — История скопированных постов

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G3 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`GET .../items` содержит `sourcePermalink`, превью текста, статус, связь с `postId` после публикации; пагинация cursor.

---

## TC-GRAB-05 — Дедуп по remote_post_id

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G4 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Два run подряд на один и тот же VK post → ровно 1 строка `grabber_items`; второй run: `items_deduped ≥ 1`, `items_new=0`; в эмуляторе сети ≤ 1 публикация.

---

## TC-GRAB-06 — Дедуп по content_hash (текст + медиа)

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G4 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Два разных `remote_post_id` с идентичным normalize(text)+media sha256 при `dedup_scope=channel` → второй `suppressed_dedup`; **0** второй публикации в эмуляторе.

---

## TC-GRAB-07 — Activate без атрибуции / чекбокса → 422

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G5 |
| Приоритет | P0 |
| Тип | негативный |
| **Автоматизация** | **авто** |

### Ожидание
`attribution.enabled=false` или `userResponsibilityAccepted=false` → 422 `ingest_attribution_required` / `ingest_responsibility_required`; статус остаётся `draft`.

---

## TC-GRAB-08 — Режим moderate (default): item в очереди

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G6 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Config | `publish_mode=moderate` (или omit → default) |

### Ожидание
Новый пост источника → item `pending_moderation`; **нет** `publications` до approve.

---

## TC-GRAB-08b — Default publish_mode при создании правила

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G6 (Q-GRAB-02) |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Create rule без поля `publish_mode` → в сохранённом config `publish_mode=moderate`. Режим `auto` принимается только при явной передаче; продукт обязан поддерживать `moderate` всегда.

---

## TC-GRAB-09 — Approve → ровно одна публикация в пайплайне

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G6; связка E5 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| **Idempotency-Key** | обязателен в тесте |

### Ожидание
Approve → создан `posts` + variant с футером атрибуции; publication → successful; повтор approve → no-op/409 без второго remote post; сверка эмулятор ↔ БД: **0 дублей**.

---

## TC-GRAB-10 — Пауза останавливает сканы

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G7 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
После `pause` новые scheduled runs не создаются; `resume` возвращает `active` и планирует `next_run_at`; уже pending publications не удаляются молча (поведение как pause поста — зафиксировать).

---

## TC-GRAB-11 — RSS 2.0 / Atom → item (default moderate) / опциональный auto

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G8, AC-G6 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Фикстуры | локальный feed XML (WireMock / Testcontainers HTTP) |

### Ожидание
Парсятся title/description/enclosure; `content_hash` стабилен; при default/`moderate` — item в очереди без публикации; при **явном** `publish_mode=auto` — пост в пайплайне; повтор опроса того же guid — дедуп.

---

## TC-GRAB-12 — SSRF: rss_url на private IP / metadata

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G9 |
| Приоритет | P0 |
| Тип | безопасность |
| **Автоматизация** | **авто** |
| Данные | `http://127.0.0.1/...`, `http://169.254.169.254/`, DNS rebinding fixture |

### Ожидание
422 `ingest_ssrf_blocked` на create/update/preview; исходящего запроса к private range нет (проверка на mock gateway).

---

## TC-GRAB-13 — Redelivery сообщения run не дублирует публикации

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G10 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Уровень | integration (RabbitMQ redelivery) |

### Ожидание
Повторная доставка `ingest.grabber.run` → items unique; при auto-publish — 1 remote post (как TC-PUB-06).

---

## TC-GRAB-14 — Ошибка публикации сети не теряет item/post

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G10; US-5.2 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Эмулятор | 5xx затем успех |

### Ожидание
Item/post живы; ретраи через publication attempts; финально 1 пост; статус item `published` / промежуточно `publish_failed` по спеке.

---

## TC-GRAB-15 — RBAC: Author/Viewer не manage/moderate

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G11 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Author: `POST rules` / `approve` → 403; `GET rules/items` своего проекта → 200 (если `automations.view` allow). Viewer: moderate → 403. Editor в чужом проекте → 404.

---

## TC-GRAB-16 — IDOR: чужой workspace / ruleId

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G11 |
| Приоритет | P0 |
| Тип | безопасность |
| **Автоматизация** | **авто** |

### Ожидание
Любой доступ к rule/item другого workspace → **404** `not_found` (не 403).

---

## TC-GRAB-17 — Аудит create / activate / approve

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G12; US-2.4 |
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
В `audit_log`: `ingest.rule.created`, `ingest.rule.activated` (с actor/IP), `ingest.item.approved`; Editor audit.view → 403.

---

## TC-GRAB-18 — Автопауза после N failed runs

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G13 |
| Приоритет | P1 |
| **Автоматизация** | **авто** |
| Config | `auto_pause_error_threshold=3` |

### Ожидание
3 подряд `failed` runs (источник 5xx) → правило `auto_paused`; уведомление Editor+; ручной resume возможен после восстановления.

---

## TC-GRAB-19 — Фильтры: exclude_ads, exclude_reposts, keywords

| Поле | Значение |
|---|---|
| Story / AC | grabber.md §3 ASSUMPTION filters |
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
Рекламный / репост / текст с exclude-keyword → `filtered_out`; include-keyword при `match=any` проходит только при совпадении.

---

## TC-GRAB-20 — Rate limit постов в час

| Поле | Значение |
|---|---|
| Story / AC | grabber.md limits |
| Приоритет | P1 |
| **Автоматизация** | **авто** |
| Config | `rate_limit_per_hour=2` |

### Ожидание
Третий новый item в окне часа не публикуется сразу: остаётся в очереди/отложен (`skip_reason=rate_limit`) без потери; после окна — доступен.

---

## TC-GRAB-21 — Website OG-extract (P1)

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G14 |
| Приоритет | P1 |
| **Автоматизация** | **кандидат в авто** |
| Feature flag | `ingest.website` |

### Ожидание
`type=website` + публичный URL фикстуры → item с title/description/image; SSRF те же запреты; без флага → 404/501.

---

## TC-GRAB-22 — Нормализация source_ids (пробелы, @screen_name)

| Поле | Значение |
|---|---|
| Story / AC | parity G2 ★ UX |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Ввод `" -123, club456 , @public "` → сохранённые ids без мусора; preview резолвит screen name; невалидный токен → 422 validation.

---

## TC-GRAB-23 — Reject item

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G6 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`reject` → `rejected`; публикаций нет; повторный скан того же remote не создаёт новый publishable item (остаётся rejected / unique key).

---

## TC-GRAB-24 — Bulk approve выбранных

| Поле | Значение |
|---|---|
| Story / AC | grabber.md bulk |
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
N items → N posts / N successful publications (или в пределах rate limit с предсказуемым остатком); частичный сбой одного id не откатывает уже идемпотентно опубликованные без компенсации-дубля.

---

## TC-GRAB-25 — Валидация лимитов целевой сети

| Поле | Значение |
|---|---|
| Story / AC | network-capabilities |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Текст/вложения сверх лимита VK → `failed_validation`, без вызова wall.post; пользователь видит причину.

---

## TC-GRAB-26 — channel_reauth_required → auto_pause

| Поле | Значение |
|---|---|
| Story / AC | grabber.md §8; US-3.5 |
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
Целевой канал переведён в reauth → правило `auto_paused`; runs не стартуют; после reconnect + resume — ок.

---

## TC-GRAB-27 — Edit-and-approve меняет текст, сохраняет атрибуцию

| Поле | Значение |
|---|---|
| Story / AC | grabber.md moderate |
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
Отредактированный текст публикуется; футер атрибуции присутствует (не удаляется без явного действия); audit `ingest.item.edit_approved`.

---

## TC-GRAB-28 — AI-rewrite (флаг E15) — smoke

| Поле | Значение |
|---|---|
| Story / AC | E17 / AC-G15 |
| Приоритет | P2 |
| **Автоматизация** | **ручной** / кандидат |

### Ожидание
При `ai_rewrite_enabled` и доступных кредитах текст уникализирован до публикации; при отказе LLM — item не теряется (fallback: оригинал + атрибуция или `failed_ingest` по политике).

---

## Чеклист качества набора

- [x] Все P0 AC-G1…G11 покрыты ≥ 1 TC
- [x] У каждого TC указаны Приоритет и Автоматизация
- [x] Для публикационных кейсов явно **0 потерь / 0 дублей**
- [x] SSRF и IDOR включены
- [x] Связь со спекой `features/grabber.md`

## История изменений

| Дата | Изменение |
|---|---|
| 2026-11-17 | Первичный набор TC-GRAB-01…28 (QA-12 / AN-P) |
