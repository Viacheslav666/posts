# QA docs

Документы агента **Posts Тестировщик**. Источники правды по процессу: `docs/specs/05-qa.md`, `docs/specs/agent-tasks.md` (QA-\*), NFR — `docs/specs/00-overview.md`.

Код автотестов живёт в `tests/` и рядом с продуктовым кодом (`backend/**/src/test`, `frontend/**/__tests__`). Здесь — стратегия, планы, кейсы и отчёты.

## Содержание

| Документ | Назначение | Задача |
|---|---|---|
| [test-strategy.md](test-strategy.md) | Уровни тестов, инструменты, окружения, нагрузка, хаос, вход/выход | QA-1 |
| [defect-classification.md](defect-classification.md) | Severity / priority, матрица, правила для публикаций | QA-1 |
| [templates/test-case.md](templates/test-case.md) | Шаблон тест-кейса | QA-1 |
| [templates/bug-report.md](templates/bug-report.md) | Шаблон баг-репорта | QA-1 |
| [test-plan-mvp.md](test-plan-mvp.md) | План MVP: scope, окружения, вход/выход, риски | QA-4 |
| [test-data.md](test-data.md) | Тестовые данные и seed-профили | QA-4 |
| [test-cases/](test-cases/) | Кейсы E1–E5 (TC-AUTH/RBAC/CH/POST/PUB) | QA-4 |
| [rbac-endpoint-map.yaml](rbac-endpoint-map.yaml) | OpenAPI operationId → RBAC action | QA-6 |
| [rbac-coverage-report.md](rbac-coverage-report.md) | Покрытие роль × эндпоинт | QA-6 |
| [traceability-tc-rbac.md](traceability-tc-rbac.md) | Трассировка TC-RBAC/IDOR → автотест | QA-6 |
| [traceability-tc-pub.md](traceability-tc-pub.md) | Трассировка TC-PUB → автотест | QA-5 |
| [traceability-tc-ad.md](traceability-tc-ad.md) | Трассировка TC-AD → автотест (ОРД / маркировка) | QA-10 |
| `release-report-vX.md` | Релизная готовность | перед релизом |

Связанные артефакты вне `docs/qa/`:

- Seed-генератор: `tests/seed/`
- Фикстуры форматирования: `docs/api/fixtures/formatting/` (≥ 50 кейсов)
- Каркас автотестов (QA-2): `tests/e2e`, `tests/contract`, `tests/load`; nightly — `.github/workflows/nightly.yml`

## Быстрые ссылки на код тестов

- `tests/e2e` — Playwright (smoke `/health` + `/api/v1/ping`, page objects)  
- `tests/contract` — Schemathesis по `docs/api/openapi.yaml`  
- `tests/load` — k6 (smoke + каркас L1/L2)  
- `tests/chaos` — хаос/DR  
- `tests/social-emulator` — эмулятор VK/TG/OK/Max  
- `tests/seed` — генератор логических seed-данных (TODO SQL после BE-2)

Локально: `make e2e-smoke` / `make contract-smoke` / `make load-smoke` (API должен отвечать на ping).
