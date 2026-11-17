# FE-2 — публичные страницы и auth-экраны

Задача: лендинг-заглушка, тарифы (PRD MVP), вход / регистрация / подтверждение email / восстановление пароля / 2FA, кнопки VK ID и Яндекс ID; формы на `react-hook-form` + `zod`; MSW-моки.

## Скриншоты

| Экран | Desktop (1280) | Mobile (390) |
|---|---|---|
| Лендинг | [landing-desktop.png](./landing-desktop.png) | [landing-mobile.png](./landing-mobile.png) |
| Тарифы | [pricing-desktop.png](./pricing-desktop.png) | [pricing-mobile.png](./pricing-mobile.png) |
| Регистрация | [register-desktop.png](./register-desktop.png) | [register-mobile.png](./register-mobile.png) |
| Вход | [login-desktop.png](./login-desktop.png) | [login-mobile.png](./login-mobile.png) |
| 2FA | [two-factor-desktop.png](./two-factor-desktop.png) | [two-factor-mobile.png](./two-factor-mobile.png) |

## Как проверить

```bash
cd frontend && pnpm install
pnpm --filter @posts/web test
pnpm --filter @posts/web typecheck
pnpm --filter @posts/web build
pnpm --filter @posts/web dev
```

E2E-заготовка регистрации:

```bash
cd tests/e2e && pnpm install && pnpm exec playwright install chromium
pnpm test:registration
```

## MSW

В `next dev` моки auth API включаются автоматически. Коды моков:

- подтверждение email: `123456`
- 2FA: email `twofa@posts.app`, код `654321`
- сброс пароля: `/reset-password?token=demo-reset-token`
