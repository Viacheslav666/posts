# Эталонные фикстуры форматирования

| | |
|---|---|
| Задача | **QA-4** |
| Источник лимитов | [`../network-capabilities.yaml`](../network-capabilities.yaml) |
| Схема кейса | [`schema.json`](schema.json) |
| Набор | [`cases.json`](cases.json) (≥ 50; QA-4 + дополнения BE-3) |
| Генератор | [`generate_cases.py`](generate_cases.py) |

## Назначение

Общий эталон для:

- бэкенд `libs/content` (JUnit) — авторитетный рендер;
- фронт `packages/domain` (Vitest) — предпросмотр;
- TC-POST-03 / US-4.3.

Каждый кейс: канонический HTML редактора → ожидаемый вывод для **Telegram** (HTML), **Max** (HTML), **VK** и **OK** (plain + предупреждение о потере разметки).

## Категории покрытия

`nested`, `specialchars` (`<>&`), `emoji`, `links`, `limits` (4096 TG / 4000 Max / 1024 caption), `spoiler`, `code`, `quote`, `plain-networks`, `unsupported`, `whitespace`, `mixed`.

## Перегенерация

```bash
python3 docs/api/fixtures/formatting/generate_cases.py
```

Потребители тестов читают `cases.json` целиком; не правят ожидаемые строки вручную без обновления генератора или явной фиксации регрессии.
