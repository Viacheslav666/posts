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
| `release-report-vX.md` | Релизная готовность | перед релизом |

Связанные артефакты вне `docs/qa/`:

- Seed-генератор: `tests/seed/`
- Фикстуры форматирования: `docs/api/fixtures/formatting/` (≥ 50 кейсов)

## Быстрые ссылки на код тестов

- `tests/e2e` — Playwright  
- `tests/load` — k6  
- `tests/chaos` — хаос/DR  
- `tests/social-emulator` — эмулятор VK/TG/OK/Max  
- `tests/seed` — генератор логических seed-данных (TODO SQL после BE-2)
