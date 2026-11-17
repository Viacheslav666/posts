# tests/load

Нагрузочный каркас **k6** (задача **QA-2**). Критерии L1–L6 — `docs/specs/05-qa.md` §6 и `docs/qa/test-strategy.md`.

## Скрипты

| Файл | Назначение |
|---|---|
| `smoke.js` | Лёгкий smoke: `GET /api/v1/ping`, 1 VU ~15 с |
| `scenarios/l1-api-mixed.js` | Каркас L1 (смешанный API); `PROFILE=full` — целевые 5k RPS / 30 мин |
| `scenarios/l2-publish-peak.js` | Каркас L2 (пик публикаций 2 000/с); полная сверка с эмулятором — QA-7 |

Общие хелперы: `lib/config.js` (`API_BASE_URL`, `WEB_BASE_URL`).

## Запуск

```bash
# установить k6: https://grafana.com/docs/k6/latest/set-up/install-k6/
# API должен отвечать на ping (make backend / make up-full / nightly)

k6 run tests/load/smoke.js
API_BASE_URL=http://127.0.0.1:8080 k6 run tests/load/smoke.js

# каркасы L1/L2 (урезанный harness-профиль по умолчанию)
k6 run tests/load/scenarios/l1-api-mixed.js
k6 run tests/load/scenarios/l2-publish-peak.js
```

Отчёты: stdout + при необходимости `k6 run --out json=reports/k6-smoke.json`.

## Nightly

Job в `.github/workflows/nightly.yml` гоняет `smoke.js` против API из compose profile `full` и сохраняет артефакт.
