# Product docs (зона Аналитика)

Источник правды по требованиям продукта Posts. Технический контракт API — `docs/api/openapi.yaml` (бэкенд); инфраструктура — `infra/`; тесты — `docs/qa/` и `tests/`.

## Артефакты AN-1 (гейт A)

| Файл | Назначение |
|---|---|
| [PRD-MVP.md](./PRD-MVP.md) | Цели, персоны, scope / out-of-scope, NFR, метрики, тарифы MVP |
| [glossary.md](./glossary.md) | Единый словарь терминов (ID для кода/API) |
| [roles-permissions.md](./roles-permissions.md) | Роли и права (человекочитаемо) |
| [roles-permissions.yaml](./roles-permissions.yaml) | Матрица роль × действие для RBAC-тестов и `PermissionEvaluator` |

## Артефакты AN-2

| Файл | Назначение |
|---|---|
| [network-capabilities.md](./network-capabilities.md) | Матрица возможностей VK / Telegram / OK / Max / Rutube |
| [`../api/fixtures/network-capabilities.yaml`](../api/fixtures/network-capabilities.yaml) | Машиночитаемые лимиты для `NetworkCapabilities` и `posts.ratelimit.*` |
| [`../api/fixtures/network-capabilities.schema.json`](../api/fixtures/network-capabilities.schema.json) | JSON Schema фикстуры |
| [`../api/fixtures/validate-network-capabilities.py`](../api/fixtures/validate-network-capabilities.py) | Проверка YAML по схеме |

## Артефакты AN-3 (stories MVP)

| Файл | Назначение |
|---|---|
| [stories/](./stories/) | User stories E1–E6 (36 шт.): Given/When/Then, приоритет, зависимости, DoR |
| [stories/README.md](./stories/README.md) | Индекс эпиков и сводка P0/P1 |
| [billing-rules.md](./billing-rules.md) | Тарифы, триал, продление, grace/freeze, промокоды, возвраты |

Создание GitHub Issues (идемпотентно, запускает **владелец**, не агент):

```bash
./infra/scripts/gh-stories-issues.sh          # dry-run
./infra/scripts/gh-stories-issues.sh --apply
```

Маркер **★** в документах — превосходство над Впостером; **≈** — паритет.

## Артефакты AN-P (паритет vposter)

| Файл | Назначение |
|---|---|
| [parity-matrix.md](./parity-matrix.md) | Матрица «функция vposter → ТЗ Posts → задача → статус → пробел» |
| [parity-open-questions.md](./parity-open-questions.md) | Вопросы к заказчику + допущения |
| [features/scheduled-posts-and-ad-marking.md](./features/scheduled-posts-and-ad-marking.md) | Отложенные посты + маркировка ОРД / ФЗ-38 |
| [features/scheduled-stories.md](./features/scheduled-stories.md) | Отложенные истории VK/OK |
| [features/grabber.md](./features/grabber.md) | Граббер / RSS / Ingest (E17) |
| QA: [`../qa/test-cases/E10-ad-marking.md`](../qa/test-cases/E10-ad-marking.md), [E11-stories.md](../qa/test-cases/E11-stories.md), [E17-grabber.md](../qa/test-cases/E17-grabber.md) | Кейсы приёмки |

Блокирует реализацию: **BE-15…17**, **FE-12…14**, **QA-10…12** (`docs/specs/agent-tasks.md`).

## Далее

- AN-4: ревью `openapi.yaml` против stories E1–E6
- AN-5: stories Фазы 2 (E10/E11/E17…) по итогам AN-P
- Фазы 2–3: `PRD-phase2.md`, `PRD-phase3.md`
