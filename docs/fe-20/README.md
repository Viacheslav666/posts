# FE-20 - дашборд, русификация, лендинг

## Скриншоты

| Экран | Desktop (1440) | Mobile (390) |
|---|---|---|
| Лендинг | [landing-desktop.png](./landing-desktop.png) | [landing-mobile.png](./landing-mobile.png) |
| Дашборд | [dashboard-desktop.png](./dashboard-desktop.png) | - |

## Проверка

```bash
cd frontend && pnpm install
pnpm --filter @posts/web test
pnpm --filter @posts/web typecheck
pnpm --filter @posts/web lint
pnpm --filter @posts/web build

cd ../tests/e2e && pnpm install && pnpm exec playwright install chromium
pnpm exec playwright test landing-dashboard-fe20.spec.ts --project=chromium
```
