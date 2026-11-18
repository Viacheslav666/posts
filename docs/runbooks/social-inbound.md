# Runbook: Social inbound (VK / Telegram / Max)

## Симптомы

- Рост `social_hook_auth_failures_total` — неверные секреты / сканирование.
- Рост `social_inbound_duplicates_total` при стабильном `social_inbound_events_total{result="handled"}` — нормальные ретраи сети.
- Рост `social_inbound_lag_seconds` p99 — ingest отстаёт или album flush задерживается.
- Подписки в статусе `degraded` / `failed`; уведомления `channel.event_subscription_degraded`.
- Хуки отвечают `503` — RabbitMQ недоступен (VK/Max/TG будут ретраить).

## Диагностика

1. Метрики Prometheus: `social_inbound_*`, глубина `ingest.vk|tg|max` и `.dlq`.
2. `GET /api/v1/channels/{id}/event-subscription` (право `automations.view`).
3. Логи api: `ingest_enqueued`, `ingest_broker_unavailable`; ingest: `social_inbound_received`, `social_inbound_duplicate`.
4. Эмулятор (staging): `/_control/vk/callback/{groupId}`, `/_control/tg/update`, `/_control/max/update`.

## Действия

| Ситуация | Действие |
|---|---|
| 503 на хуках | Проверить RabbitMQ, quorum-очереди `ingest.*`, сеть api→broker. После восстановления сети сами дошлют события. |
| `degraded` VK | `POST .../event-subscription/repair` (`automations.manage`); проверить `groups.getCallbackServers` и group token. |
| `degraded` TG / `gap` | Простой > 24 ч: события потеряны у Telegram. Repair перерегистрирует webhook; историю не восстановить. |
| Max отписался (8 ч) | Health-job должен переподписать; иначе repair. Проверить TLS:443 и секрет. |
| DLQ рост | Смотреть poison payload; x-delivery-limit=10. Не репроцессить секреты из raw (их там нет). |
| Auth failures | Ротация secret через ensure/repair; проверить, что VK шлёт `secret` в теле. |

## Алерты (рекомендуемые)

- `social_hook_auth_failures_total` rate > 1/s за 5m — warning.
- `ingest.*.dlq` messages > 0 — warning; > 100 — critical.
- `social_inbound_lag_seconds` p99 > 30s за 10m — warning.
- Доля подписок `failed` > 5% — critical.
