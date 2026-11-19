# Отчёт нагрузки: P2-автоматизации (QA-14)

Дата шаблона: 2026-11-19.  
Инструмент: **k6** (`tests/load`). Полные целевые профили — **nightly** с `PROFILE=full` и compose `full`; в PR-CI и по умолчанию в workflow — урезанный **`PROFILE=smoke`** (сниженные `rate` / `duration`), если в run не указано иное.

## Сводка harness-целей (приёмка BE-32…BE-36)

| Сценарий | Скрипт / IT | Цель (full) | Пороги (full) | Smoke / CI по умолчанию |
|---|---|---|---|---|
| Inbound webhooks | `tests/load/scenarios/l3-social-inbound-hooks.js` | **2000** событий/с на 1 инстанс `posts-api` | p99 &lt; **200 мс**; `social_inbound_hook_lost==0` | `INBOUND_RATE=50`, `INBOUND_DURATION=15s` |
| Antispam verdicts | `tests/load/scenarios/l5-antispam-verdicts.js` (каркас QA-14) | **3000** комментариев/с (синтетика ingest+handler) | p99 вердикта &lt; **5 мс**; потерь **0** | `ANTISPAM_RATE=100`, 30 с |
| Crosspost fan-out | `tests/load/scenarios/l6-crosspost-fanout.js` (каркас QA-14) | **1000** исходных постов/мин × **3** приёмника | дублей **0**, петель **0**; p95 end-to-end &lt; **60 с** (без `delay`) | `XP_RATE=20/min`, 5 мин |
| Suggest decisions | `tests/load/scenarios/l4-suggest-ingest.js` (каркас) | **200** suggest/с (распределение по каналам) | p95 «событие→решение» &lt; **2 с** | rate ÷ 20 |

> **CI / nightly:** в `.github/workflows/nightly.yml` сейчас зафиксирован только k6 **`smoke.js`**. Сценарии L3–L6 подключаются в job QA-14 с `PROFILE=smoke`, если явно не задан `PROFILE=full` (staging / ручной прогон перед релизом).

## Inbound — 2000/s

| Метрика | Цель (full) | Smoke |
|---|---|---|
| `http_req_duration` p99 | &lt; 200 ms | &lt; 500 ms |
| `http_req_failed` | &lt; 1% | &lt; 1% |
| `social_inbound_hook_lost` | **0** | **0** |
| Длительность | 30 s (настраиваемо) | 15 s |
| Предусловия | Seed: `CHANNEL_ID`, `VK_SECRET`, `GROUP_ID`; Rabbit + ingest up | Те же, rate ↓ |

```bash
# Smoke (nightly default)
PROFILE=smoke INBOUND_RATE=50 k6 run tests/load/scenarios/l3-social-inbound-hooks.js

# Full (приёмка BE-32)
PROFILE=full INBOUND_RATE=2000 INBOUND_DURATION=30s k6 run tests/load/scenarios/l3-social-inbound-hooks.js
```

Сверка **0 потерь** после прогона: `Qa14InboundLoadSmokeIT` (глубина `ingest.vk` → 0, строки в `social_inbound_events` = отправленным уникальным `event_id`).

## Antispam — 3000/s

| Метрика | Цель (full) | Smoke |
|---|---|---|
| Вердикт engine (синт.) p99 | &lt; 5 ms | &lt; 20 ms |
| Потери событий | **0** | **0** |
| Нарушение rate-limit VK/TG | **0** (Bucket4j / execute batch) | выборочная проверка |
| Профиль | 50k каналов (логическое шардирование VU) | 10 каналов |

```bash
PROFILE=smoke ANTISPAM_RATE=100 k6 run tests/load/scenarios/l5-antispam-verdicts.js
PROFILE=full  ANTISPAM_RATE=3000 k6 run tests/load/scenarios/l5-antispam-verdicts.js
```

Дополнительно: `Qa14AntispamLoadIT` (Testcontainers + эмулятор) — контроль очереди `actions.moderation.*` и отсутствия `failed_permanent` сверх SLA.

## Crosspost — 1000/min × 3 приёмника

| Метрика | Цель (full) | Smoke |
|---|---|---|
| Исходных постов/мин | **1000** | 20 |
| Приёмников на правило | **3** | 3 |
| Дубликаты в эмуляторе | **0** | **0** |
| Обратные публикации (петли) | **0** | **0** |
| p95 (без `delay_minutes`) | &lt; 60 s | &lt; 120 s |

```bash
PROFILE=smoke XP_RATE=20 k6 run tests/load/scenarios/l6-crosspost-fanout.js
PROFILE=full  XP_RATE=1000 XP_TARGETS=3 k6 run tests/load/scenarios/l6-crosspost-fanout.js
```

Сверка: `GET /_control/verify` на эмуляторе + `crosspost_items` / `publications` в БД (`Qa14CrosspostLoadIT`).

## Лимиты соцсетей (контроль при интерпретации отчёта)

| Сеть | Ограничение | Источник |
|---|---|---|
| VK user key | 3 rps | `network-capabilities.md` |
| VK community | 20 rps | idem |
| VK `execute` | ≤ 25 вызовов | BE-33 |
| TG | ~1 msg/s чат, 20/min группа | Bot API FAQ |
| Max | 30 rps | dev.max.ru |

Нагрузочные сценарии **не** должны стабильно превышать эти лимиты на один токен; при full-прогоне используется пул эмуляторных «каналов» / VU.

## Артефакты

| Окружение | Путь артефакта |
|---|---|
| Локально | `tests/load/reports/k6-*.json` |
| Nightly GH | workflow artifact `k6-smoke.json` (+ QA-14 отчёты после подключения L3–L6) |

## История

| Дата | Комментарий |
|---|---|
| 2026-11-19 | Каркас отчёта QA-14; L4–L6 — заготовки сценариев в `tests/load/scenarios/` |