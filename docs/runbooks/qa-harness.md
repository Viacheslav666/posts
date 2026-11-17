# QA-харнес: e2e / contract / load (QA-2)

## Назначение

Локальный и CI/nightly каркас:

| Пакет | Инструмент | Smoke |
|---|---|---|
| `tests/e2e` | Playwright | UI `/health`, API `/api/v1/ping` |
| `tests/contract` | Schemathesis | OpenAPI ↔ живой API (ping / nightly) |
| `tests/load` | k6 | `smoke.js` + каркас L1/L2 |

## Быстрый smoke без compose

```bash
cd backend && ./gradlew :apps:api:bootJar
java -jar apps/api/build/libs/api-*.jar --spring.profiles.active=openapi &

make e2e-smoke
make contract-smoke
make load-smoke   # нужен установленный k6
```

## Smoke против полного стека

```bash
make up-full
PLAYWRIGHT_SKIP_WEBSERVER=1 make e2e-smoke
make contract-smoke
make load-smoke
```

Profile `full` уже в `main` (DEV-4). Nightly (`.github/workflows/nightly.yml`) поднимает `api` + `web` + зависимости через этот профиль, гоняет Schemathesis / Playwright / k6 и сохраняет отчёты артефактами.

## CI

- PR: job `e2e-smoke` в `ci.yml` — API на профиле `openapi`, Playwright webServer, Schemathesis smoke, артефакты.
- Ночь: `nightly.yml` (schedule 02:00 UTC + `workflow_dispatch`).
