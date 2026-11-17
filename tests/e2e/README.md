# tests/e2e

Каркас Playwright. FE-2 добавил заготовку сценария регистрации (`specs/registration.spec.ts`) на MSW-моках веб-приложения.

## Запуск

```bash
# из корня репозитория
cd tests/e2e
pnpm install
pnpm exec playwright install chromium
pnpm test:registration
```

Переменные:

- `PLAYWRIGHT_BASE_URL` — по умолчанию `http://127.0.0.1:3000`
- `PLAYWRIGHT_SKIP_WEBSERVER=1` — не поднимать `next dev` (если сервер уже запущен)
- `NEXT_PUBLIC_API_MOCKING=true` — включается webServer-ом конфига

Дальнейшее расширение — задача QA-2.
