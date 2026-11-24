# Граббер / Ingest - спецификация функции (E17)

| Поле | Значение |
|---|---|
| Версия | **1.0.1** |
| Дата | **2026-11-17** |
| Статус | Готово к реализации (DoR); patch: Q-GRAB-02 - `moderate` default |
| Эпик | **E17** - RSS-импорт и граббер (единый модуль Ingest) |
| Задачи | BE-17, BE-17b (сайты P1), FE-14, QA-12 |
| Конкурент | [vposter.ru](https://vposter.ru/) - раздел «Граббер» / «RSS-парсер» |
| Архитектура | Java 21, Spring Boot; `apps/ingest`; таблица `automation_rules` (`type` + `config` jsonb); публикационный конвейер scheduler → RabbitMQ → publisher; RBAC workspace; идемпотентность; **0 потерь / 0 дублей** |
| Связанные ТЗ | `docs/specs/02-backend.md` §10 (Фаза 2, `automation_rules`); `docs/specs/01-analyst.md` E17; `docs/product/parity-matrix.md` §5; `docs/adr/0004-publishing-pipeline.md` |
| Открытые вопросы | [`../parity-open-questions.md`](../parity-open-questions.md) |

### Источники

| Источник | URL / путь | Дата |
|---|---|---|
| Справка vposter: Граббер | https://help.vposter.ru/informaciya-neobkhodimaya-dlya-ispolzovaniya-po/avto-ustanovki/grabber | 2026-11-17 |
| Справка vposter: RSS-парсер | https://help.vposter.ru/informaciya-neobkhodimaya-dlya-ispolzovaniya-po/avto-ustanovki/rss-parser | 2026-11-17 |
| Функциональные характеристики vposter | https://help.vposter.ru/funkcionalnye-kharakteristiki | 2026-11-17 |
| Внутренний анализ Posts | `docs/analysis.md` §2.4 | 2026-11-17 |
| Матрица паритета | `docs/product/parity-matrix.md` (G1–G9) | 2026-11-17 |
| Backend Phase 2 | `docs/specs/02-backend.md` §10 | 2026-11-17 |
| OpenAPI (расширение) | `docs/api/openapi.yaml` | предлагается в §7 |

> **ASSUMPTION.** Публичная справка vposter описывает граббер кратко (целевое сообщество, ID источников через запятую без пробелов, «Добавить в сообщество», «Активные сообщества», скан раз в несколько часов, ручная проверка, история, юр. предупреждение). Полный набор UI-фильтров кабинета **не документирован публично**. Ниже поля с пометкой **ASSUMPTION** - продуктовые допущения Posts для паритета и превосходства; уточняются скриншотами ЛК / ответами владельца в `parity-open-questions.md`.

---

## 1. Цель и границы (scope)

### 1.1. Цель

Дать SMM-командам и паблик-мейкерам **автоматический импорт контента** из внешних источников в каналы Posts с последующей публикацией через **тот же** надёжный пайплайн, что и ручные/отложенные посты: без потерь, без дублей, с аудитом, RBAC и обязательной атрибуцией источника.

В vposter граббер и RSS - **разные** авто-установки. В Posts они объединяются в **единый модуль Ingest (E17)** с общим UX «правило → источники → фильтры → действие → журнал».

### 1.2. In-scope (фаза E17 / BE-17)

| Приоритет | Источник | Приёмник (MVP E17) | Примечание |
|---|---|---|---|
| **P0** | VK-группы / страницы (по ID / screen name) | VK-канал workspace | Паритет vposter Grabber |
| **P0** | RSS 1.0 / 2.0 / Atom | VK-канал workspace (далее - любой подключённый канал по матрице) | Единый Ingest; vposter - отдельный RSS-парсер |
| **P1** | Веб-сайты (HTML / Open Graph / article extract) | VK (+ опционально TG) | SSRF-защита обязательна; BE-17b |
| **P2+** | Telegram-каналы / чаты как источник | VK / TG / OK / Max | После стабилизации E16 кросспостинг / прав доступа |
| **P2+** | Одноклассники как источник | VK / OK | По мере API-доступа |

### 1.3. Out of scope (этот документ)

- Автопостинг предложки (E18), антиспам (E19), кросспостинг как отдельный UX (E16) - соседние модули на той же `automation_rules`.
- AI-рерайт / суммаризация (E15) - ★ дифференциатор, подключается к Ingest как опциональный шаг; контракт - отдельная story.
- Meta (Instagram*/Facebook*) как источник или приёмник - **окончательно вне scope** (Q-STORY-05 / паритет-матрица).
- Браузерное расширение «копировать пост» (аналог Впостер Tools) - Фаза 3.

### 1.4. Принципы

1. **Один пайплайн публикации.** Grabbed/RSS item → нормализованный черновик/пост → `publications` → scheduler → RabbitMQ → publisher. Нельзя публиковать «в обход» идемпотентности.
2. **0 потерь / 0 дублей.** Дедуп по `content_hash` (sha256 текста + медиа) + ключ источника `(network, source_id, remote_post_id)`; идемпотентный ingest run.
3. **Юридическая безопасность по умолчанию.** Обязательная атрибуция источника + чекбокс ответственности пользователя до активации правила.
4. **Workspace-first.** Правила живут в проекте/workspace; права - из `roles-permissions`; чужой workspace → 404.

---

## 2. Пользовательские сценарии

### 2.1. Happy path - граббер VK → VK

1. Owner/Admin/Editor открывает **Автоматизации → Ingest → Граббер**.
2. Выбирает **целевой VK-канал** (подключён, статус `ok`, права на стену).
3. Указывает **источники**: ID или `@screen_name` групп через запятую (UI нормализует пробелы; API принимает массив).
4. Настраивает фильтры, интервал, режим публикации (сразу / очередь модерации / по расписанию слотов).
5. Включает **атрибуцию** (обязательно), подтверждает **чекбокс ответственности** (КоАП 7.12 / УК 146 / ToS VK).
6. Нажимает «Создать правило» → правило в статусе `active`, появляется в списке активных.
7. Scheduler/ingest раз в N минут (по `scan_interval_minutes`) опрашивает источники, создаёт `grabber_items`. По умолчанию (`publish_mode=moderate`) items ждут в очереди модерации; при явно выбранном `auto` - сразу в пайплайн.
8. Пользователь видит **историю** скопированных постов (статус, ссылка на оригинал, ссылка на публикацию).
9. Кнопка **«Проверить сейчас»** запускает внеочередной run (с rate-limit).

### 2.2. Очередь модерации (**решение Q-GRAB-02**)

1. Режим `publish_mode=moderate` - **обязательная возможность продукта** и **значение по умолчанию** при создании правила. Режим `auto` - **опционален** (пользователь может переключить явно; UI предупреждает о рисках копирайта/спама).
2. При `moderate` новые items → статус `pending_moderation`; публикаций в сеть **нет** до approve.
3. Editor+ видит очередь: превью, оригинал, предложенный текст с футером атрибуции.
4. Действия: **Опубликовать** / **Отклонить** / **Редактировать и опубликовать** / массовые «опубликовать выбранные».
5. Author может создавать/смотреть правила только если выдано право `automations.view` + draft - см. §10; публиковать из очереди - только Editor+.

### 2.3. Дедупликация

1. Источник публикует пост A; граббер копирует → `content_hash=H1`, `remote_post_id=R1`.
2. Повторный скан того же поста → item не создаётся повторно (unique key).
3. Источник правит текст → новый hash; поведение: **ASSUMPTION** по умолчанию **не** перепубликовывать правки (`ignore_edits=true`); опция `regrab_on_edit` - P2.
4. Два разных источника с идентичным текстом+медиа → второй отбрасывается по `content_hash` в рамках целевого канала/workspace (настраиваемо: `dedup_scope`).

### 2.4. Пауза / автопауза

1. Пользователь ставит правило на **паузу** → новые runs не стартуют; уже созданные publications в пайплайне живут по правилам постов (pause post отдельно).
2. **ASSUMPTION автопауза:** при N подряд failed runs / ошибке токена источника / лимите тарифа / `channel_reauth_required` правило → `auto_paused` + уведомление Owner/Admin/Editor.

### 2.5. Атрибуция и копирайт

1. Без включённой атрибуции и без чекбокса ответственности правило **нельзя** активировать (API 422).
2. В конец текста (или первый комментарий - **ASSUMPTION** опция) добавляется футер вида:  
   `Источник: «Название» - https://vk.com/wall-123_456`  
   (шаблон настраиваемый, плейсхолдеры `{source_name}`, `{source_url}`, `{author}`).
3. История хранит `source_permalink` для аудита.

### 2.6. RSS happy path

1. Тип правила `rss`: URL фида, целевой канал, фильтры (ключевые слова, типы вложений из enclosure).
2. Парсер RSS/Atom; SSRF-guard на URL; TTL/ETag/Last-Modified для экономного опроса.
3. Item → тот же путь, что у граббера (hash, модерация, пайплайн).

### 2.7. Сайт (P1)

1. Тип `website`: список URL или sitemap / CSS/selectors (**ASSUMPTION** минимально - URL статьи или RSS-подобный список ссылок с OG-extract).
2. Жёсткий SSRF deny (private IP, link-local, metadata endpoints); DNS rebinding protection (`02-backend.md` § security).

---

## 3. UI: полная таблица полей

Контекст экрана: **Автоматизации → Ingest → создать/редактировать правило**.  
Типы правил в UI: «Граббер VK», «RSS», «Сайт» (P1) - внутри одна сущность `automation_rules`.

| field id | Label (RU) | Тип | Default | Валидация | Visibility | Поведение |
|---|---|---|---|---|---|---|
| `name` | Название правила | string | «Граббер {channel}» | 1–120 символов | всегда | Отображается в списке активных |
| `type` | Тип источника | enum `grabber` \| `rss` \| `website` | `grabber` | required | всегда | Переключает блок источников; `website` - P1 / feature flag |
| `project_id` | Проект | uuid select | текущий | project in workspace | всегда | Scope RBAC |
| `target_channel_id` | Целевое сообщество / канал | channel select | - | канал `network` совместим с матрицей; статус ≠ `reauth_required` | всегда | Аналог «выбрать сообщество» vposter |
| `source_ids` | ID источников | string[] / textarea «через запятую» | `[]` | для `grabber`: 1–50 ID; `^-?\d+$` или `@?[A-Za-z0-9._]+`; **без обязательности «без пробелов»** - UI trim/split | `type=grabber` | Паритет vposter; нормализация пробелов ★ |
| `source_labels` | Подписи источников | map id→name | авто из API | - | `type=grabber`, read-mostly | Кэш имён для атрибуции |
| `rss_url` | URL RSS/Atom | url | - | http(s); SSRF-safe; content-type feed | `type=rss` | ETag/Last-Modified |
| `rss_max_items_per_run` | Макс. записей за прогон | int | `20` | 1–100 | `type=rss` | **ASSUMPTION** |
| `website_urls` | URL страниц / sitemap | url[] | `[]` | SSRF-safe; ≤ 20 | `type=website` (P1) | **ASSUMPTION** |
| `website_selector` | CSS-селектор статьи | string | - | optional | `type=website` advanced | **ASSUMPTION** |
| `scan_interval_minutes` | Интервал проверки | int / select | `180` (3 ч) | ∈ {15,30,60,120,180,360,720,1440}; тариф может резать минимум | всегда | Паритет «раз в несколько часов»; ручной run отдельно |
| `manual_check_enabled` | Кнопка «Проверить сейчас» | bool (UI) | `true` | rate limit: ≤ 1 / 5 мин / правило | всегда | **ASSUMPTION** лимит |
| `publish_mode` | Режим публикации | enum `moderate` \| `auto` \| `draft` | **`moderate`** | required; значение `moderate` всегда доступно | всегда | **РЕШЕНИЕ Q-GRAB-02** (не ASSUMPTION): default и обязательный режим продукта; `auto` - опционально |
| `schedule_mode` | Когда публиковать | enum `immediate` \| `delay` \| `slot` | `immediate` | - | `publish_mode≠draft` | **ASSUMPTION** |
| `publish_delay_minutes` | Задержка после обнаружения | int | `0` | 0–10080 | `schedule_mode=delay` | **ASSUMPTION** |
| `content_slot_id` | Слот контент-плана | uuid | - | slot of project | `schedule_mode=slot` (E9) | P2, если слоты ещё нет - скрыто |
| `keyword_include` | Ключевые слова (включить) | string[] | `[]` | ≤ 50; match mode ниже | advanced | **ASSUMPTION** |
| `keyword_exclude` | Ключевые слова (исключить) | string[] | `[]` | ≤ 50 | advanced | **ASSUMPTION** |
| `keyword_match` | Режим совпадения | enum `any` \| `all` \| `whole_word` | `any` | - | если keywords не пусты | **ASSUMPTION** (как у предложки vposter) |
| `attachment_types` | Типы вложений | multi enum `photo` \| `video` \| `audio` \| `doc` \| `poll` \| `link` \| `none` | все | - | advanced | **ASSUMPTION** |
| `require_attachments` | Только с вложениями | bool | `false` | - | advanced | **ASSUMPTION** |
| `exclude_ads` | Исключать рекламные посты | bool | `true` | - | `type=grabber` | **ASSUMPTION** (VK `marked_as_ads`) |
| `exclude_reposts` | Исключать репосты | bool | `true` | - | `type=grabber` | **ASSUMPTION** |
| `exclude_copyrighted_music` | Пропускать посты с copyright audio | bool | `false` | - | `type=grabber` | **ASSUMPTION** P2 |
| `text_max_length` | Обрезать текст до | int | по лимиту сети | ≤ network max | advanced | Иначе truncate + «…» |
| `strip_hashtags` | Удалять хештеги | bool | `false` | - | advanced | **ASSUMPTION** |
| `strip_mentions` | Удалять @упоминания | bool | `false` | - | advanced | **ASSUMPTION** |
| `prepend_text` | Текст в начало | string | `""` | ≤ 500 | advanced | **ASSUMPTION** |
| `append_text` | Текст в конец (до атрибуции) | string | `""` | ≤ 500 | advanced | **ASSUMPTION** |
| `attribution_enabled` | Указывать источник | bool | `true` (locked on) | must be true для `active` | всегда | ★ юр. требование Posts; в vposter - предупреждение |
| `attribution_placement` | Куда ставить атрибуцию | enum `footer` \| `first_comment` \| `both` | `footer` | - | всегда | **ASSUMPTION** |
| `attribution_template` | Шаблон атрибуции | string | `Источник: {source_name} - {source_url}` | плейсхолдеры whitelist | advanced | Обязателен непустой |
| `user_responsibility_accepted` | Чекбокс ответственности | bool | `false` | must `true` при activate | всегда | Блокирует activate; текст со ссылкой на ToS / КоАП 7.12 / УК 146 |
| `rate_limit_per_hour` | Лимит постов/час на правило | int | `10` | 1–60 | advanced | **ASSUMPTION**; защита целевого сообщества |
| `rate_limit_per_day` | Лимит постов/сутки | int | `50` | 1–500 | advanced | **ASSUMPTION** |
| `auto_pause_on_errors` | Автопауза при ошибках | bool | `true` | - | advanced | **ASSUMPTION** |
| `auto_pause_error_threshold` | Порог подряд failed runs | int | `5` | 1–20 | если auto_pause | **ASSUMPTION** |
| `dedup_enabled` | Дедупликация | bool | `true` | - | advanced | Выключение - только Owner/Admin + предупреждение |
| `dedup_scope` | Область дедупа | enum `rule` \| `channel` \| `workspace` | `channel` | - | если dedup | **ASSUMPTION** |
| `ignore_edits` | Не перезабирать правки | bool | `true` | - | advanced | **ASSUMPTION** |
| `stop_words_list_id` | Чёрный список (shared) | uuid | - | optional | advanced | **ASSUMPTION** P2 |
| `ai_rewrite_enabled` | AI-рерайт перед публикацией | bool | `false` | нужен AI-кредит | advanced (E15) | ★; вне MVP E17 core |
| `status` | Статус | enum | `draft` | см. §8 | read + actions | pause/resume/archive |
| `created_at` / `updated_at` | Служебные | datetime | - | - | read-only | - |
| `last_run_at` | Последняя проверка | datetime | null | - | список/карточка | - |
| `next_run_at` | Следующая проверка | datetime | - | - | список | - |
| `stats.copied_total` | Скопировано всего | int | 0 | - | список | из агрегата items |

### 3.1. Элементы списка «Активные правила» (паритет «Активные сообщества»)

| UI | Поведение |
|---|---|
| Название, тип, целевой канал, источники (кратко) | клик → редактирование |
| Статус badge | `active` / `paused` / `auto_paused` / `error` |
| «Проверить сейчас» | POST run |
| Пауза / Возобновить | PATCH status |
| История | ссылка на items |
| Последняя ошибка | tooltip |

### 3.2. История / очередь items

Колонки: дата обнаружения, источник, превью текста, вложения (иконки), `content_hash` (скрыто), статус item, ссылка на оригинал, ссылка на пост Posts / remote, действия модерации.

---

## 4. Матрица поддержки сетей

| Источник ↓ \ Приёмник → | VK | Telegram | OK | Max | Rutube |
|---|:---:|:---:|:---:|:---:|:---:|
| **VK wall** (grabber) | **P0** | P2 | P2 | P2 | - |
| **RSS/Atom** | **P0** | P1 | P1 | P1 | - |
| **Website/HTML** | **P1** | P1 | P2 | P2 | - |
| **Telegram channel** | P2 | P2 | P2 | P2 | - |
| **OK** | P2 | P2 | P2 | - | - |

Легенда: P0 - E17 core; P1 - BE-17b / следом; P2 - позже; «-» - нецелесообразно / нет API.

Ограничения приёмника - `network-capabilities` (длина текста, число вложений, форматы). При несовместимости item → `failed_validation` с причиной, без публикации.

---

## 5. Модель данных

Расширение Фазы 2 (`02-backend.md` §10): правила на общей таблице + журналы граббера.

```sql
-- Общая таблица автоматизаций (уже в ТЗ Фазы 2)
automation_rules (
  id              uuid PRIMARY KEY,
  workspace_id    uuid NOT NULL,
  project_id      uuid NOT NULL,
  type            text NOT NULL,  -- 'grabber' | 'rss' | 'website' | …
  name            text NOT NULL,
  target_channel_id uuid NOT NULL,
  status          text NOT NULL,  -- draft|active|paused|auto_paused|archived
  config          jsonb NOT NULL, -- см. поля UI / GrabberConfig
  config_version  int NOT NULL DEFAULT 1,
  user_responsibility_accepted_at timestamptz,
  user_responsibility_accepted_by uuid,
  last_run_at     timestamptz,
  next_run_at     timestamptz,
  consecutive_failures int NOT NULL DEFAULT 0,
  created_by      uuid NOT NULL,
  created_at      timestamptz NOT NULL,
  updated_at      timestamptz NOT NULL,
  version         int NOT NULL DEFAULT 0  -- optimistic lock
);
CREATE INDEX ON automation_rules (workspace_id, type, status);
CREATE INDEX ON automation_rules (status, next_run_at) WHERE status = 'active';

-- Опциональный view/alias продуктового языка
-- grabber_rules := automation_rules WHERE type IN ('grabber','rss','website')

grabber_runs (
  id              uuid PRIMARY KEY,
  rule_id         uuid NOT NULL REFERENCES automation_rules(id),
  workspace_id    uuid NOT NULL,
  trigger         text NOT NULL,  -- scheduled|manual|retry
  status          text NOT NULL,  -- running|succeeded|succeeded_partial|failed
  started_at      timestamptz NOT NULL,
  finished_at     timestamptz,
  sources_scanned int NOT NULL DEFAULT 0,
  items_found     int NOT NULL DEFAULT 0,
  items_new       int NOT NULL DEFAULT 0,
  items_deduped   int NOT NULL DEFAULT 0,
  items_published int NOT NULL DEFAULT 0,
  error_code      text,
  error_message   text,
  trace_id        text
);
CREATE INDEX ON grabber_runs (rule_id, started_at DESC);

grabber_items (
  id              uuid PRIMARY KEY,
  rule_id         uuid NOT NULL REFERENCES automation_rules(id),
  run_id          uuid REFERENCES grabber_runs(id),
  workspace_id    uuid NOT NULL,
  source_network  text NOT NULL,       -- vk|rss|website|…
  source_id       text NOT NULL,       -- group id / feed url hash / …
  remote_post_id  text NOT NULL,       -- wall-123_456 | guid | url
  source_permalink text,
  source_name     text,
  content_text    text,
  content_html    text,
  attachments_json jsonb NOT NULL DEFAULT '[]',
  content_hash    char(64) NOT NULL,   -- sha256 hex
  media_hashes    text[] NOT NULL DEFAULT '{}', -- sha256 каждого медиа
  status          text NOT NULL,       -- см. §8
  skip_reason     text,
  post_id         uuid,                -- posts.id после создания
  publication_ids uuid[],
  moderated_by    uuid,
  moderated_at    timestamptz,
  raw_payload     jsonb,               -- усечённый оригинал для дебага
  detected_at     timestamptz NOT NULL,
  created_at      timestamptz NOT NULL,
  updated_at      timestamptz NOT NULL
);

-- Идемпотентность на уровне источника
CREATE UNIQUE INDEX uq_grabber_items_remote
  ON grabber_items (rule_id, source_network, source_id, remote_post_id);

-- Дедуп по контенту в выбран dedup_scope (пример для channel - через rule→channel в приложении
-- либо денормализованный target_channel_id)
CREATE UNIQUE INDEX uq_grabber_items_hash_channel
  ON grabber_items (workspace_id, content_hash, /* target_channel_id denorm */ rule_id)
  WHERE status NOT IN ('rejected','suppressed_dedup');
-- Точная форма уникальности hash уточняется при реализации под dedup_scope (open-questions).
```

### 5.1. `config` jsonb (GrabberConfig) - логическая схема

```json
{
  "source_ids": ["-123", "club456"],
  "rss_url": null,
  "scan_interval_minutes": 180,
  "publish_mode": "moderate",
  "schedule_mode": "immediate",
  "publish_delay_minutes": 0,
  "filters": {
    "keyword_include": [],
    "keyword_exclude": [],
    "keyword_match": "any",
    "attachment_types": ["photo", "video", "doc", "link", "none"],
    "require_attachments": false,
    "exclude_ads": true,
    "exclude_reposts": true
  },
  "transform": {
    "prepend_text": "",
    "append_text": "",
    "strip_hashtags": false,
    "strip_mentions": false,
    "text_max_length": null
  },
  "attribution": {
    "enabled": true,
    "placement": "footer",
    "template": "Источник: {source_name} - {source_url}"
  },
  "limits": {
    "rate_limit_per_hour": 10,
    "rate_limit_per_day": 50
  },
  "reliability": {
    "dedup_enabled": true,
    "dedup_scope": "channel",
    "ignore_edits": true,
    "auto_pause_on_errors": true,
    "auto_pause_error_threshold": 5
  },
  "ai_rewrite_enabled": false
}
```

### 5.2. Расчёт `content_hash`

```
content_hash = sha256(
  normalize_ws(text)
  + "\n"
  + join(sorted(media_sha256_list), ",")
  + "\n"
  + join(sorted(attachment_canonical_ids), ",")  // для VK: type+owner_id+media_id
)
```

Медиа перед хешированием загружается в media-pipeline (или хешируется по байтам после download); при недоступности медиа - hash только текста + remote attachment ids (**ASSUMPTION**, зафиксировать в open-questions).

---

## 6. Поток обработки (runtime)

```
posts-scheduler / ShedLock
  → due automation_rules (type grabber|rss|website, status=active, next_run_at<=now)
  → сообщение ingest.grabber.run {ruleId, trigger}
       queue: ingest.grabber (quorum) / DLQ

posts-ingest
  1. Создать grabber_runs (running)
  2. Для каждого источника: fetch (VK API / RSS / HTTP) с rate limit + Resilience4j
  3. Нормализация → кандидаты
  4. Фильтры config.filters
  5. Для каждого кандидата:
       - INSERT grabber_items ON CONFLICT DO NOTHING (remote key)
       - если dedup hash conflict → status=suppressed_dedup
       - иначе status=pending_moderation (default moderate) | approved→publish (если явный auto)
  6. Если auto/draft: создать post + variants (+ attribution) в одной транзакции;
       schedule/publish через существующий API-доменный сервис (тот же outbox)
  7. Обновить next_run_at, consecutive_failures, run stats
  8. Метрики: grabber_items_new, grabber_deduped, grabber_run_duration
```

Ручной run: тот же путь, `trigger=manual`, отдельный rate limit.

### 6.1. Медиа-пайплайн (вложения граббера)

При создании поста (auto-режим или одобрение из очереди) `GrabberMediaResolver` превращает вложения источника в VK string attachments (`content.stringAttachments`, ≤ 10) целевого поста:

| Вложение | Как попадает в пост |
|---|---|
| Фото | Скачивается (≤ `posts.grabber.media.max-photo-bytes`, 20 МБ) и перезаливается в целевое сообщество: `photos.getWallUploadServer` → upload → `photos.saveWallPhoto`. При ошибке — ссылка на оригинал (`photo{owner}_{id}_{access_key}`), в лог пишется предупреждение. |
| Видео | `transform.reupload_video=false` — ссылка на оригинал. `true` — публичное видео ≤ 50 МБ (`max-video-bytes`) скачивается по `video.get` → `files.mp4_*` и загружается через `video.save` (`group_id` цели). Если перезалить не удалось (внешнее, приватное, недоступно, нет файла, > 50 МБ, нет токена) — пост **не публикуется**: item `publish_failed`, `skip_reason=video_reupload_failed:<code>`, событие `grabber.item_failed` (если `reliability.notify_on_errors`). Повтор — кнопкой «Повторить» в очереди. |
| Клипы, музыка, плейлисты, файлы, опросы, альбомы, товары, подборки, вики-страницы | Ссылка на оригинал `type{owner}_{id}[_{access_key}]`. |
| Ссылка / статья | Одна ссылка-сниппет (URL). |

Фильтры «Не публиковать»: `skip_if_no_*` / `skip_if_has_*` для фото, видео, клипа, музыки, файла, товара, опроса, статьи; `skip_if_no_visual_media` — нет ни фото, ни видео, ни клипа. Клип не считается видео.

Удаление (`transform.strip_*`) выполняется до разрешения медиа. `transform.user_attachments` («Свои вложения»: `photo-1_2, video-1_3`, `[rand]a|b[/rand]`, `[keyTextEntry]вложения|слово1,слово2[/keyTextEntry]`) добавляется, только если после очистки вложений не осталось; синтаксис проверяется при сохранении правила (422). `transform.attachments_mode=carousel` → `primary_attachments_mode=carousel`, если в посте ≥ 2 фото/видео.

Загрузка выполняется токеном администратора целевого сообщества (social account канала, расшифровка через workspace DEK); без него — сервисный токен граббера. Для не-VK каналов вложения пока не переносятся.

---

## 7. Предлагаемые OpenAPI-эндпоинты

Расширение `docs/api/openapi.yaml` (tag **Ingest** / **Automations**). Префикс `/api/v1`. Ошибки - RFC 7807 ProblemDetail + `code`. IDOR → 404. Мутации - `Idempotency-Key` где указано.

| Method | Path | Описание | Permission |
|---|---|---|---|
| `GET` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/ingest/rules` | Список правил (`type` filter: grabber,rss,website) | `automations.view` |
| `POST` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/ingest/rules` | Создать правило (body: GrabberRuleCreate) | `automations.manage` |
| `GET` | `/api/v1/ingest/rules/{ruleId}` | Карточка правила | `automations.view` |
| `PATCH` | `/api/v1/ingest/rules/{ruleId}` | Изменить config/name; `version` optimistic lock → 409 | `automations.manage` |
| `POST` | `/api/v1/ingest/rules/{ruleId}/activate` | Активировать (проверка attribution + responsibility) | `automations.manage` |
| `POST` | `/api/v1/ingest/rules/{ruleId}/pause` | Пауза | `automations.manage` |
| `POST` | `/api/v1/ingest/rules/{ruleId}/resume` | Снять паузу / auto_paused | `automations.manage` |
| `POST` | `/api/v1/ingest/rules/{ruleId}/archive` | Архив | `automations.manage` |
| `POST` | `/api/v1/ingest/rules/{ruleId}/runs` | Ручной запуск (`Idempotency-Key`) | `automations.manage` |
| `GET` | `/api/v1/ingest/rules/{ruleId}/runs` | История runs (cursor) | `automations.view` |
| `GET` | `/api/v1/ingest/rules/{ruleId}/items` | История/очередь items (`status`, cursor) | `automations.view` |
| `GET` | `/api/v1/ingest/items/{itemId}` | Детали item + raw preview | `automations.view` |
| `POST` | `/api/v1/ingest/items/{itemId}/approve` | Модерация: опубликовать (`Idempotency-Key`) | `automations.moderate` |
| `POST` | `/api/v1/ingest/items/{itemId}/reject` | Модерация: отклонить | `automations.moderate` |
| `POST` | `/api/v1/ingest/items/bulk` | Массовые approve/reject | `automations.moderate` |
| `POST` | `/api/v1/ingest/items/{itemId}/edit-and-approve` | Правка текста/медиа → post → publish | `automations.moderate` |
| `POST` | `/api/v1/ingest/preview` | Превью: разбор источника / RSS без сохранения | `automations.view` |

### 7.1. Фрагменты схем (логика для openapi)

```yaml
# Предлагаемые schemas (не вмержены в openapi.yaml этим документом - задача BE-17)
GrabberRuleCreate:
  type: object
  required: [type, name, targetChannelId, config, userResponsibilityAccepted]
  properties:
    type: { type: string, enum: [grabber, rss, website] }
    name: { type: string, minLength: 1, maxLength: 120 }
    targetChannelId: { type: string, format: uuid }
    config: { $ref: '#/components/schemas/GrabberConfig' }
    userResponsibilityAccepted: { type: boolean }
    activate: { type: boolean, default: false }

GrabberItem:
  type: object
  properties:
    id: { type: string, format: uuid }
    status: { type: string }
    sourceNetwork: { type: string }
    sourcePermalink: { type: string, format: uri }
    contentText: { type: string }
    contentHash: { type: string, pattern: '^[a-f0-9]{64}$' }
    postId: { type: string, format: uuid, nullable: true }
    detectedAt: { type: string, format: date-time }
```

Коды ошибок (дополнение к существующим):  
`ingest_responsibility_required`, `ingest_attribution_required`, `ingest_source_unreachable`, `ingest_ssrf_blocked`, `ingest_rate_limited`, `ingest_auto_paused`, `ingest_validation_failed`, `ingest_dedup_conflict` (информативно при preview).

---

## 8. Состояния и ошибки

### 8.1. Статусы правила (`automation_rules.status`)

| Статус | Смысл | Переходы |
|---|---|---|
| `draft` | Создано, не активно | → `active` (activate) |
| `active` | Сканируется по расписанию | → `paused`, `auto_paused`, `archived` |
| `paused` | Пауза пользователем | → `active`, `archived` |
| `auto_paused` | Пауза системой | → `active` (resume после фикса), `archived` |
| `archived` | Терминальный (read-only) | - |

### 8.2. Статусы run

`running` → `succeeded` | `succeeded_partial` | `failed`

### 8.3. Статусы item

| Статус | Смысл |
|---|---|
| `discovered` | Найден, ещё не прошёл фильтры (краткоживущий) |
| `filtered_out` | Отклонён фильтрами |
| `suppressed_dedup` | Дубль по remote key или content_hash |
| `pending_moderation` | Ждёт модератора |
| `rejected` | Отклонён модератором |
| `approved` | Одобрен / auto; пост создаётся |
| `scheduled` | Пост в пайплайне (publications pending/queued) |
| `published` | Хотя бы одна publication succeeded |
| `publish_failed` | Пост создан, публикация failed_permanent (пост жив, retry как у E5) |
| `failed_validation` | Не прошёл лимиты сети / медиа |
| `failed_ingest` | Ошибка скачивания медиа / парсинга |

### 8.4. Типовые ошибки UX/API

| Ситуация | HTTP | `code` | Поведение |
|---|---|---|---|
| Нет чекбокса ответственности | 422 | `ingest_responsibility_required` | Activate запрещён |
| attribution_enabled=false | 422 | `ingest_attribution_required` | Activate запрещён |
| SSRF / private IP в RSS URL | 422 | `ingest_ssrf_blocked` | Не сохранять опасный URL |
| Источник недоступен / VK API 15 | run failed / partial | `ingest_source_unreachable` | consecutive_failures++ |
| Ручной run чаще лимита | 429 | `ingest_rate_limited` / `rate_limited` | Retry-After |
| Канал требует reauth | auto_pause | `channel_reauth_required` | Уведомление |
| Optimistic lock | 409 | `optimistic_lock_conflict` | Как у posts |
| Чужой workspace | 404 | `not_found` | IDOR |
| Нет права | 403 | `forbidden` | на своём WS |

---

## 9. Надёжность

| Требование | Реализация |
|---|---|
| Идемпотентный ingest | Unique `(rule_id, source_network, source_id, remote_post_id)`; повторный run не создаёт второй item |
| Дедуп контента | `content_hash` = sha256(normalize(text) + media hashes); scope из config |
| Идемпотентная публикация | Создание post → тот же E5-конвейер: `publications.idempotency`, VK `guid`, findExisting |
| Нет double-publish | Approve item с уже `post_id` → no-op / 409; bulk approve идемпотентен по item id |
| Retries fetch | Resilience4j на чтение источников; ошибки источника **не** ретраят публикацию вслепую |
| Retries publish | Только через publication state machine (E5), не внутри ingest loop |
| At-least-once очередь | Manual ack; дубль сообщения run → второй run безопасен (unique items) |
| 0 потерь | Успешно обнаруженный кандидат либо в items, либо в DLQ run с алертом; не «проглатывать» без статуса |
| 0 дублей в сети | Запрет второго wall.post для того же item; дедуп до создания post |
| Наблюдаемость | `trace_id` на run; метрики; аудит; алерт при auto_pause и DLQ |
| Медиа | Скачивание через posts-media / временный storage; кэш по sha256; вирусы/тип - Tika |

---

## 10. RBAC

Новые action ID (добавить в `roles-permissions.yaml` при реализации E17):

| Action | Owner | Admin | Editor | Author | Viewer |
|---|:---:|:---:|:---:|:---:|:---:|
| `automations.view` | ✔ | ✔ | ◐ | ◐ | ◐ |
| `automations.manage` | ✔ | ✔ | ◐ | – | – |
| `automations.moderate` | ✔ | ✔ | ◐ | – | – |

◐ - только назначенные проекты (`assigned_projects`).

Маппинг на сценарии:

- Создать/изменить/пауза/ручной run → `automations.manage`
- Очередь модерации approve/reject → `automations.moderate` (и фактически `posts.schedule` при публикации)
- Author: просмотр правил и items своего проекта - **ASSUMPTION** allow `automations.view`; без manage/moderate (как «не публикует сам»)
- Viewer: только view, без превью raw_payload чувствительных полей - **ASSUMPTION** raw скрыт
- Публикация из граббера создаёт posts от имени актора модерации / system bot `ingest@posts` с `created_by` = актор approve или `rule.created_by` для auto - **ASSUMPTION** зафиксировать в open-questions

Связь с каналами: управлять правилом на канал можно только если канал в scope проекта; подключение канала по-прежнему `channels.manage`.

---

## 11. Аудит-события

Пишутся в `audit_log` (US-2.4), `entity = ingest_rule | ingest_item | ingest_run`.

| `action` | Когда | `diff` (примерно) |
|---|---|---|
| `ingest.rule.created` | POST rule | type, target_channel_id, config snapshot |
| `ingest.rule.updated` | PATCH | json patch / before-after |
| `ingest.rule.activated` | activate | responsibility_by, at |
| `ingest.rule.paused` | pause | reason=user |
| `ingest.rule.resumed` | resume | - |
| `ingest.rule.auto_paused` | система | error_code, threshold |
| `ingest.rule.archived` | archive | - |
| `ingest.run.started` | run create | trigger |
| `ingest.run.finished` | run end | stats |
| `ingest.item.approved` | moderate | item_id, post_id |
| `ingest.item.rejected` | moderate | reason |
| `ingest.item.edit_approved` | edit-and-approve | fields changed |
| `ingest.bulk_moderated` | bulk | ids, action |

Просмотр: `audit.view` (Owner/Admin). IP + actor обязательны.

---

## 12. Правовые требования

1. **Авторское право.** Копирование чужого контента без разрешения правообладателя может квалифицироваться по **КоАП РФ ст. 7.12** и **УК РФ ст. 146**. Posts **не** является правообладателем контента источников.
2. **ToS VK / сетей.** Пользователь обязан соблюдать правила платформ (в т.ч. запрет спама, вводящего в заблуждение контента, нарушений исключительных прав).
3. **Обязательная атрибуция.** В Posts нельзя активировать правило без `attribution.enabled=true` и непустого шаблона. ★ жёстче предупреждения vposter.
4. **Чекбокс ответственности.** Текст (нормативный): пользователь подтверждает, что имеет право использовать контент или действует в рамках исключений закона, указывает источник, несёт ответственность за публикации. Фиксируются `accepted_at`, `accepted_by`.
5. **Хранение доказательств.** `source_permalink`, `content_hash`, audit - для разбора жалоб (≥ срок ретенции audit).
6. **Персональные данные.** Не собирать лишние ПДн из источников; raw_payload усекать; доступ по RBAC.
7. **Маркировка рекламы.** Если исходный пост `marked_as_ads` и не исключён фильтром - **не** публиковать без отдельного ORD-потока (E10); default `exclude_ads=true`.

---

## 13. Критерии приёмки и тест-кейсы

Детальные TC: [`docs/qa/test-cases/E17-grabber.md`](../../qa/test-cases/E17-grabber.md) (`TC-GRAB-*`).

### 13.1. Acceptance criteria (сводка)

| ID | Критерий | Приоритет |
|---|---|---|
| AC-G1 | Можно создать правило grabber VK→VK с источниками и целевым каналом; появляется в активных | P0 |
| AC-G2 | Периодический скан по `scan_interval_minutes` + ручной run | P0 |
| AC-G3 | История items с permalink источника и статусом | P0 |
| AC-G4 | Дедуп: повтор того же remote_post_id и того же content_hash не создаёт вторую публикацию | P0 |
| AC-G5 | Без attribution + responsibility activate → 422 | P0 |
| AC-G6 | Default `publish_mode=moderate` → очередь; approve → один пост; `auto` опционален | P0 |
| AC-G7 | Пауза останавливает новые runs; resume продолжает | P0 |
| AC-G8 | RSS 1.0/2.0/Atom → item → пайплайн | P0 |
| AC-G9 | SSRF на rss_url/website блокируется | P0 |
| AC-G10 | Публикация только через E5-пайплайн; 0 дублей при redelivery run | P0 |
| AC-G11 | RBAC: Author не manage; Viewer не moderate; чужой WS → 404 | P0 |
| AC-G12 | Аудит на create/activate/approve | P1 |
| AC-G13 | Автопауза после N ошибок + уведомление | P1 |
| AC-G14 | Website extract P1 | P1 |
| AC-G15 | AI-rewrite optional | P2 |

---

## 14. Преимущества Posts vs vposter

| Тема | vposter | Posts ★ |
|---|---|---|
| Граббер и RSS | Разные разделы | **Единый Ingest** (grabber + RSS + websites) |
| Команда | Нет ролей | Workspace RBAC, аудит, очередь модерации для команды |
| Надёжность | Жалобы на сбои в отзывах | Тот же SLA-пайплайн: идемпотентность, ретраи, 0 дублей, метрики |
| Дедуп | Упомянут косвенно | Явный `content_hash` + remote key + scope |
| Юридика | Предупреждение в справке | **Обязательная** атрибуция + чекбокс + audit trail |
| Фильтры | Не полностью в open docs | Прозрачная модель полей + open-questions вместо «магии» |
| Источники | VK (+ RSS отдельно) | VK + RSS + сайты P1; TG/OK later |
| AI | Отдельный чат | Опциональный рерайт/суммаризация в цепочке Ingest (E15) |
| UX источников | «ID без пробелов» | Нормализация ввода, screen names, preview |
| Наблюдаемость | История копий | Runs, items, trace_id, алерты auto_pause |

---

## 15. Открытые допущения → `parity-open-questions.md`

Следующие пункты **должны** быть перечислены/сверены в [`parity-open-questions.md`](../parity-open-questions.md):

| ID | Вопрос | Влияние |
|---|---|---|
| Q-G1 | Полный список UI-полей граббера в ЛК vposter (скриншоты) | Паритет фильтров |
| Q-G2 | Точный интервал скана «раз в несколько часов» у vposter | Default `scan_interval_minutes` |
| Q-G3 | ~~Очередь модерации vs auto~~ → **закрыто Q-GRAB-02:** default `moderate`, `auto` опционально | - |
| Q-G4 | Как vposter обрабатывает рекламные посты и репосты | Defaults `exclude_ads` / `exclude_reposts` |
| Q-G5 | Лимиты «постов в час/сутки» и автопауза - факт или только у нас | Тарифные лимиты |
| Q-G6 | Формат футера источника у vposter (если есть) | Шаблон attribution |
| Q-G7 | Дедуп: только remote id или ещё по тексту/медиа | Алгоритм hash |
| Q-G8 | Поведение при edit исходного поста | `ignore_edits` |
| Q-G9 | Кто `created_by` у auto-published постов | RBAC/аудит |
| Q-G10 | Нужен ли first_comment для атрибуции как опция паритета | `attribution_placement` |
| Q-G11 | Website grabber: селекторы vs только OG | Scope P1 |
| Q-G12 | Тарифные минимумы интервала скана | Биллинг |

До закрытия вопросов поля с меткой **ASSUMPTION** в §3 считаются продуктовым решением Posts v1.0 и могут быть изменены без breaking API при `config_version`.

---

## История изменений

| Версия | Дата | Изменение |
|---|---|---|
| 1.0 | 2026-11-17 | Первая полная спецификация E17 Ingest/Grabber (AN-P) |
| 1.0.1 | 2026-11-17 | Q-GRAB-02: `publish_mode=moderate` default и обязателен; `auto` опционально; enum `moderate`\|`auto`\|`draft` |
