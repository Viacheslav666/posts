# `@posts/api-client`

Типизированный клиент Posts API.

- Типы генерируются из `docs/api/openapi.yaml` (`openapi-typescript` → `src/schema.d.ts`).
- HTTP-клиент - `openapi-fetch` (`createPostsApiClient`).
- **Не редактируйте** `schema.d.ts` вручную.

```bash
# из frontend/
pnpm gen:api
```

Обёртка приложения (base URL, refresh-cookie, ProblemDetail, query keys) живёт в
`apps/web/src/shared/api-client/`.
