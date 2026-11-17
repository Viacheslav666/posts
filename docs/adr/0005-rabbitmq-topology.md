# ADR-0005: Топология RabbitMQ

- Статус: accepted
- Дата: 2026-11-17
- Авторы: Posts Разраб Бэк (BE-1)

## Контекст

Публикационный конвейер (ADR-0004) и смежные контуры (медиа, ingest, уведомления, события) требуют предсказуемой топологии: изоляция сетей, DLQ, горизонтальное масштабирование потребителей (KEDA), отсутствие потери сообщений при отказе узла. Брокер — RabbitMQ 3.13, клиент — Spring AMQP. Источник: `02-backend.md` §5.2–5.5, `00-overview.md` §4.

## Решение

### Общие правила

- Все рабочие очереди — **quorum queues** (репликация, безопаснее classic mirrored).
- Publisher confirms + `mandatory` на исходящих publish из scheduler/outbox-relay.
- Consumers: manual ack, prefetch ограничен (publish: 50), virtual-thread executor.
- Заголовок `traceparent` (W3C) пробрасывается во все сообщения.
- `x-delivery-limit` на publish/actions-очередях → после лимита доставки сообщение уходит в DLX (защита от poison pill при requeue-сценариях).

### Publish (ядро MVP)

| Объект | Тип | Ключ / аргументы |
|---|---|---|
| Exchange `publish` | direct, durable | routing key = `<network>` (`vk`, `tg`, `ok`, `max`, `rutube`) |
| Queue `publish.<network>` | quorum | bound to `publish` with RK `<network>`; DLX `publish.dlx`; `x-delivery-limit` |
| Exchange `publish.dlx` | direct, durable | |
| Queue `publish.<network>.dlq` | quorum | bound to `publish.dlx` with RK `<network>` |

Сообщение: `{ "publicationId": "<uuid>", "traceparent": "…" }` (минимальный payload; состояние в PG).

### Actions (delete / unpin / first_comment / edit)

| Объект | Тип |
|---|---|
| Exchange `actions` | direct, durable |
| Queue `actions.<network>` | quorum + DLX `actions.dlx` → `actions.<network>.dlq` |

Тот же `publisher-<network>` слушает и `publish.<network>`, и `actions.<network>`.

### Прочие контуры

| Exchange | Тип | Очереди (примеры) | Потребитель |
|---|---|---|---|
| `media` | direct/quorum dest | `media.process` (+ DLQ) | posts-media |
| `ingest` | direct | `ingest.vk`, `ingest.tg`, `ingest.max` (+ DLQ) | posts-ingest |
| `notify` | topic/direct | `notify.email`, `notify.push`, `notify.tg` (+ DLQ) | posts-notifications |
| `events` | topic | `events.#` (фаза 2: inbox, automations, analytics) | фаза 2 |

Outbox-relay (scheduler) публикует доменные события после commit; имена `event_type` совпадают с routing key где применимо.

### Именование и код

- Декларация топологии — `libs/messaging` (beans `Declarables` / `RabbitAdmin`), переиспользуется apps.
- Имя сети publisher’а — `posts.publisher.network` / env `POSTS_PUBLISHER_NETWORK`.
- KEDA: ScaledObject по длине `publish.<network>` и `actions.<network>`.

## Альтернативы

- **Classic queues + mirroring** — отвергнуто: quorum — рекомендуемый HA-путь RabbitMQ 3.13.
- **Одна очередь на все сети** — отвергнуто: нет изоляции отказов и независимого масштабирования.
- **Отдельный брокер на контур** — избыточно для MVP; при росте можно вынести `events`/аналитику.

## Последствия

- Devops добавляет quorum-политику и алерты на глубину очередей / DLQ в Helm/monitoring (DEV-задачи).
- Новая сеть = новая очередь `publish.<network>` + DLQ + Deployment publisher + KEDA trigger.
- Ретраи публикации **не** через TTL/DLX delay topology, а через `next_attempt_at` в PostgreSQL (ADR-0004).
- Интеграционные тесты (Testcontainers RabbitMQ) проверяют binding’и и DLX в BE-4/BE-10.
