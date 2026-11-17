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
| `test-plan-mvp.md` | План MVP, риски, scope | QA-4 |
| `test-cases/E*.md` | Кейсы по эпикам | QA-4+ |
| `test-data.md` | Тестовые данные и seed | QA-4 |
| `release-report-vX.md` | Релизная готовность | перед релизом |

## Быстрые ссылки на код тестов

- `tests/e2e` — Playwright  
- `tests/load` — k6  
- `tests/chaos` — хаос/DR  
- `tests/social-emulator` — эмулятор VK/TG/OK/Max  
