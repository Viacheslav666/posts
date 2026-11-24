# Runbook: Crosspost (BE-36)

## Назначение

Кросспостинг VK / Telegram / Max → VK, OK, Telegram, Max с многоуровневой защитой от петель (`libs:crosspost`).

## Симптомы

- Рост `crosspost_loop_blocked_total{layer=L1|L2|L3|L4|L7}` - срабатывает защита (ожидаемо при A↔B).
- Рост `crosspost_items_total{status="failed"}` - публикации падают (смотреть publisher / сеть).
- `crosspost_items_total{status="skipped_unsupported_media"}` - файлы TG >20 МБ без Local Bot API или Max >100 МБ.
- `crosspost_items_total{status="auto_paused"}` / правила в `auto_paused` - сработал L6.
- `crosspost_source_to_copy_seconds` p95 без `delay_minutes` > 60 с - отставание deferred processor / publisher.

## Диагностика

1. Метрики: `crosspost_items_total`, `crosspost_loop_blocked_total`, `crosspost_source_to_copy_seconds`.
2. Журнал: `GET /api/v1/crosspost/rules/{id}/items` (`automations.view`).
3. Статус правила и `config.auto_pause_reason` при `auto_paused`.
4. Ingest: deferred poll (`posts.crosspost.defer-poll-ms`, default 2s) и `process_after` у `received`.
5. Publisher / `remote_post_registry` / `publish_intents` для L1–L2.
6. Feature flag `automation.crosspost`, лимит плана `crosspost_rules`.

## Действия

| Ситуация | Действие |
|---|---|
| Ложные `skipped_own` | Проверить реестр и origin; опции `include_posts_published_via_posts` / `include_suggest_published`. |
| Петля A↔B | Ожидаемо 0 обратных копий; смотреть L3/L1. Не отключать L1–L4. |
| `auto_paused` | Разобрать причину в config; `activate` после фикса; снизить `circuit_max_items` / `circuit_max_loop_skips` при необходимости. |
| TG >20 МБ | Настроить `posts.telegram.local-bot-api-url` или принять skip. |
| Дубли на приёмнике | L4 SimHash; проверить два пути A→C и B→C. |
| Retry failed | `POST /api/v1/crosspost/items/{id}/retry` (`automations.moderate`) - тот же idempotency key. |
| Удаления Max | В v1 не синхронизируются; follow-up P2. |

## Алерты (рекомендуемые)

- `crosspost_items_total{status="failed"}` rate > 5/min - warning.
- Доля правил `auto_paused` > 5% - warning.
- `crosspost_source_to_copy_seconds` p95 > 60s (без delay) за 15m - warning.
- `crosspost_loop_blocked_total{layer="L3"}` spike без активных двусторонних правил - проверить гонки L2.

## Тесты

- Unit: `libs:crosspost` filters/transform/loop (≥40).
- IT: emulator VK→TG/OK/Max, TG album→VK, петли A↔B / треугольник / L4, retry×5, ad_post_excluded.
