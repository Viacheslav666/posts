# ADR-0013: Входящие события соцсетей

- Статус: accepted
- Дата: 2026-11-18
- Авторы: Posts Разраб Бэк (BE-32)

## Контекст

Предложка VK, антиспам и кросспостинг (фаза 2) требуют надёжного приёма входящих событий: VK Callback API, Telegram Bot webhook, Max webhook. До BE-32 `SocialHooksController` только логировал payload (`LoggingIngestPublisher`) - события терялись. ADR-0005 уже предусматривал очереди `ingest.vk|tg|max`.

Ограничения сетей (проверка 2026-11-18):

- **VK:** ответ `ok` + 200 на Callback; повторы 10с/3м/10м/30м/1ч; `secret` в JSON; `event_id`; один Callback-сервер на наше URL (лимит серверов, ошибка 2000).
- **Telegram:** webhook и `getUpdates` взаимоисключающие; обновления ≤ 24 ч; альбомы - `media_group_id`; `X-Telegram-Bot-Api-Secret-Token`.
- **Max:** только HTTPS:443; `X-Max-Bot-Api-Secret`; до 10 повторов; автоотписка после 8 ч неуспеха.

## Решение

1. **Подписка** `channel_event_subscriptions` на канал: mode, secret_enc (DEK, ADR-0006), consumers (`suggest|antispam|crosspost`), event_types, status. `EventSubscriptionService.ensure/release` идемпотентны (optimistic lock `version`). На одно VK-сообщество - один наш Callback-сервер.
2. **Хуки** на `posts-api`: быстрая проверка секрета (constant-time), для VK - confirmation code из БД, ответ `ok`/`text/plain` **до** тяжёлой обработки; при недоступности Rabbit - `503` + `Retry-After`.
3. **Транспорт:** exchange `ingest`, quorum-очереди `ingest.vk|tg|max` + DLX/DLQ, `x-delivery-limit`, publisher confirms. `RabbitIngestPublisher` заменяет logging-реализацию (logging остаётся для `openapi` / отсутствия брокера).
4. **Ingest:** нормализация → `SocialInboundEvent`, дедуп в `social_inbound_events` (партиции по дням, ретеншн 14 д, raw ≤ 64 KiB без секретов), SPI `SocialEventHandler` (`order`, `CONSUMED` останавливает цепочку). Альбомы TG агрегируются окном 3 с.
5. **Здоровье:** `SubscriptionHealthJob` (ShedLock) опрашивает Callback/Webhook/subscriptions, ставит `degraded`/`failed`, чинит с backoff, уведомляет через outbox.
6. **Безопасность:** лимит тела 1 МБ; секреты не в логах/raw; rate-limit на хук по IP/каналу (Bucket4j при включённом `posts.ratelimit`); ArchUnit: `/_control/**` только в эмуляторе.

## Альтернативы

- Обработка в хуке синхронно - отвергнуто: p99 < 200 мс и ретраи сетей требуют быстрый ack + очередь.
- Одна очередь `ingest.social` - отвергнуто (ADR-0005): изоляция сетей.
- Long Polling Max/TG в проде - только `local`/`test`.

## Последствия

- Модули предложки/антиспама/кросспостинга регистрируют consumer через `ensure` и реализуют `SocialEventHandler`.
- Кастомный TG-бот канала: `setWebhook` забирает обновления у других сервисов → API warning `custom_bot_webhook_takeover`.
- Миграция V26; runbook `docs/runbooks/social-inbound.md`.
