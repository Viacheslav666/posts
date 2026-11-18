# tests/e2e

Каркас **Playwright** (TypeScript) для UI/smoke e2e платформы Posts. Задача **QA-2**.

## Структура

```
pages/          page objects (Health, Register, …)
helpers/        общий API-клиент для request-smoke
specs/          сценарии (smoke, registration stub)
playwright.config.ts
```

## Переменные окружения

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `PLAYWRIGHT_BASE_URL` | `http://127.0.0.1:3000` | origin веб-приложения |
| `API_BASE_URL` | `http://127.0.0.1:8080` | origin `posts-api` для `/api/v1/ping` |
| `PLAYWRIGHT_SKIP_WEBSERVER` | — | `1` = не поднимать `next dev` (compose `full`) |
| `CI` | — | ретраи, junit, запрет `test.only` |

## Локальный запуск

```bash
# 1) Frontend deps (webServer Playwright поднимает next dev с MSW)
pnpm --dir frontend install

# 2) API для ping-smoke — любой из вариантов:
#    a) быстрый профиль без БД (только System/ping и stubs):
cd backend && ./gradlew :apps:api:bootJar
java -jar backend/apps/api/build/libs/*.jar --spring.profiles.active=openapi &
#    b) полный локальный стек:
make up-full   # затем PLAYWRIGHT_SKIP_WEBSERVER=1

# 3) e2e
cd tests/e2e
pnpm install
pnpm exec playwright install chromium
pnpm test:smoke
```

Отчёт HTML: `tests/e2e/playwright-report/`. В CI/nightly дополнительно `reports/junit.xml`.

## Smoke-сценарии

| Спека | Что проверяет |
|---|---|
| `specs/smoke.spec.ts` | `GET /health` (UI), `GET /api/v1/ping`, корень `/` |
| `specs/registration.spec.ts` | FE-2 stub регистрации на MSW |
| `specs/grabber-moderation.spec.ts` | FE-14 / TC-GRAB P0 на MSW (очередь moderate, approve) |
| `specs/grabber-fullstack.spec.ts` | QA-12 full-stack (только `E2E_GRABBER_FULLSTACK=1`, nightly) |

## CI / nightly

- PR: job `e2e-smoke` в `.github/workflows/ci.yml` (API на профиле `openapi` + Playwright webServer).
- Ночь: `.github/workflows/nightly.yml` против compose profile `full`, артефакты отчётов.
