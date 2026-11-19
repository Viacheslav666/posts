# Отчёт нагрузки: P2-автоматизации (QA-14)

Дата: 2026-11-19.  
Инструмент: **k6** (`tests/load`).

**Замера p95/p99 нет.** Full-профиль (2000 событий/с, 3000 комментариев/с, 1000 постов/мин) в этом PR не гонялся, числа ниже — цели приёмки, не результат прогона. Скриптов `l4-suggest-ingest.js`, `l5-antispam-verdicts.js`, `l6-crosspost-fanout.js` в репозитории нет.

Nightly запускает только smoke и только если заданы `vars.QA14_CHANNEL_ID` и `secrets.QA14_VK_SECRET`. Без них шаг завершается ошибкой (прогон без цели не считается успешным). `|| true` не используется.

## Сводка harness-целей (приёмка BE-32…BE-36)

| Сценарий | Скрипт, который реально есть | Цель (full), не измерена | Что скрипт проверяет |
|---|---|---|---|
| Inbound webhooks | `tests/load/scenarios/l3-social-inbound-hooks.js` | **2000**/с, p99 &lt; **200 мс**, потерь 0 | HTTP 200 и тело `ok`. По умолчанию rate=2000; smoke в nightly задаёт `INBOUND_RATE=50` |
| Antispam hooks | `tests/load/scenarios/l4-p2-antispam-events.js` | **3000** комментариев/с, p99 вердикта &lt; **5 мс** | только хук VK `wall_reply_new` (200 + тело `ok`). Вердикт движка и 50k каналов не меряет |
| Crosspost hooks | `tests/load/scenarios/l5-p2-crosspost-ingest.js` | **1000** постов/мин × **3** приёмника, p95 &lt; **60 с**, дублей 0 | только хук `wall_post_new`. Дубли, петли и 3 приёмника не сверяет |
| Suggest decisions | скрипта нет | **200** suggest/с, p95 &lt; **2 с** | не автоматизировано |

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

Сверки глубины очереди отдельным IT нет (`Qa14InboundLoadSmokeIT` не существует).

## Antispam — 3000/s

| Метрика | Цель (full) | Smoke |
|---|---|---|
| Вердикт engine (синт.) p99 | &lt; 5 ms | &lt; 20 ms |
| Потери событий | **0** | **0** |
| Нарушение rate-limit VK/TG | **0** (Bucket4j / execute batch) | выборочная проверка |
| Профиль | 50k каналов (логическое шардирование VU) | 10 каналов |

```bash
PROFILE=smoke ANTISPAM_RATE=30 CHANNEL_ID=... VK_SECRET=... k6 run tests/load/scenarios/l4-p2-antispam-events.js
PROFILE=full  ANTISPAM_RATE=3000 CHANNEL_ID=... VK_SECRET=... k6 run tests/load/scenarios/l4-p2-antispam-events.js
```

`Qa14AntispamLoadIT` нет. Скрипт не проверяет очередь `actions.moderation.*`.

## Crosspost — 1000/min × 3 приёмника

| Метрика | Цель (full) | Smoke |
|---|---|---|
| Исходных постов/мин | **1000** | 20 |
| Приёмников на правило | **3** | 3 |
| Дубликаты в эмуляторе | **0** | **0** |
| Обратные публикации (петли) | **0** | **0** |
| p95 (без `delay_minutes`) | &lt; 60 s | &lt; 120 s |

```bash
PROFILE=smoke CROSSPOST_RATE=5 CHANNEL_ID=... VK_SECRET=... k6 run tests/load/scenarios/l5-p2-crosspost-ingest.js
PROFILE=full  CROSSPOST_RATE=17 CHANNEL_ID=... VK_SECRET=... k6 run tests/load/scenarios/l5-p2-crosspost-ingest.js
```

`Qa14CrosspostLoadIT` нет. Скрипт не ходит в `/_control/verify` и не считает дубли.

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
| 2026-11-19 | Каркас. Full p95/p99 не снимались. Реальные скрипты: `l3-social-inbound-hooks.js`, `l4-p2-antispam-events.js`, `l5-p2-crosspost-ingest.js` |