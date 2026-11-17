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

## Далее

- AN-4: ревью `openapi.yaml` против stories E1–E6
- Фазы 2–3: `PRD-phase2.md`, `PRD-phase3.md`
