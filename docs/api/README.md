# API

- `openapi.yaml` - code-first контракт, генерируется из `backend/apps/api` (`./gradlew :apps:api:generateOpenApiDocs`).
- `fixtures/` - эталонные фикстуры форматирования и capabilities.

## BE-3 (контракт ядра)

Контроллеры-заглушки и DTO в `backend/apps/api` описывают операции auth, me, workspaces, members, invitations, projects, channels, channel-sets, networks/capabilities, posts, calendar, publications, media, notifications, billing, audit, trial.

- Ошибки: RFC 7807 `ProblemDetail` + поле `code` (каталог в `ProblemCodes` / `info.description` OpenAPI).
- Пагинация: курсорная (`cursor`, `limit`) → **типизированные** `*Page`-схемы (`SessionPage`, `PostPage`, …), не generic envelope.
- `GET /api/v1/me` в профиле `local` возвращает фикстуру; остальные операции - HTTP 501 до соответствующих спринтов.
- После изменения Java-контракта: `make openapi` (или `./gradlew :apps:api:generateOpenApiDocs` + `pnpm --filter @posts/api-client gen:api`) и коммит `openapi.yaml` + TS-клиента.
- Follow-up AN-4 (гейт C): см. PR `fix/be-3-contract-review` после merge #18.
