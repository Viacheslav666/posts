# Product docs (зона Аналитика)

Источник правды по требованиям продукта Posts. Технический контракт API — `docs/api/openapi.yaml` (бэкенд); инфраструктура — `infra/`; тесты — `docs/qa/` и `tests/`.

## Артефакты AN-1 (гейт A)

| Файл | Назначение |
|---|---|
| [PRD-MVP.md](./PRD-MVP.md) | Цели, персоны, scope / out-of-scope, NFR, метрики, тарифы MVP |
| [glossary.md](./glossary.md) | Единый словарь терминов (ID для кода/API) |
| [roles-permissions.md](./roles-permissions.md) | Роли и права (человекочитаемо) |
| [roles-permissions.yaml](./roles-permissions.yaml) | Матрица роль × действие для RBAC-тестов и `PermissionEvaluator` |

Маркер **★** в документах — превосходство над Впостером; **≈** — паритет.

## Далее (не в этом PR)

- AN-2: `network-capabilities.md` + fixtures
- AN-3: `stories/E1…E6-*.md`, `billing-rules.md`
- Фазы 2–3: `PRD-phase2.md`, `PRD-phase3.md`
