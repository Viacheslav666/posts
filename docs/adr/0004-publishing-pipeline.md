# ADR-0004: Публикационный конвейер

- Статус: accepted
- Дата: 2026-11-17
- Авторы: Posts Разраб Бэк (BE-1)

## Контекст

Ключевое обещание продукта: пост выходит вовремя (p99 ≤ 60 с) или пользователь узнаёт об ошибке; **0 потерь и 0 дублей** в соцсетях при пике 2 000 публикаций/с. PostgreSQL - единственный источник истины; RabbitMQ - транспорт. Детали исполнения - `02-backend.md` §5.

## Решение

### Поток

```
API (schedule) → publications (pending) + outbox
       ↓
scheduler: SKIP LOCKED batch → state=queued → RabbitMQ publish.<network>
       ↓
publisher-<network>: claim in_progress → SocialAdapter → succeeded | failed_*
       ↓
reaper: stuck queued/in_progress → failed_retryable
```

### Диспетчер (`posts-scheduler`)

Каждую секунду каждый инстанс (без лидера) через `JdbcClient`:

```sql
WITH due AS (
  SELECT id FROM publications
  WHERE state IN ('pending','failed_retryable')
    AND coalesce(next_attempt_at, run_at) <= now() + interval '5 seconds'
  ORDER BY coalesce(next_attempt_at, run_at)
  LIMIT 500
  FOR UPDATE SKIP LOCKED)
UPDATE publications p SET state='queued', locked_until = now() + interval '10 minutes'
FROM due WHERE p.id = due.id
RETURNING p.id, p.network, …;
```

После коммита - `RabbitTemplate` с publisher confirms (`correlated` + `mandatory`); тело сообщения: `publicationId` + `traceparent`. Не подтверждённые брокером → возврат в `pending`.

### Publisher (один образ на сеть)

1. Claim: `UPDATE … SET state='in_progress' … WHERE id=:id AND state='queued'` - 0 строк → ack (дубль доставки).
2. Идемпотентность: если `remote_post_id` заполнен → `succeeded`; иначе `adapter.findExisting(idempotencyKey)` (VK `guid`, TG/OK/Max - эвристики по контенту).
3. `idempotency_key = sha256(variant_id || run_at)` - уникален в БД.
4. HTTP к соцсети **вне** транзакции БД; лимиты Bucket4j/Redis; Resilience4j CB/TimeLimiter/Bulkhead.
5. Короткий Retry Resilience4j - только для идемпотентных GET и загрузок медиа; **повтор публикации - только через БД** (`failed_retryable` + `next_attempt_at`).
6. Backoff: 5 с … 15 мин, ≤ 8 попыток или 60 мин после `run_at`.
7. Необработанное исключение → nack без requeue → DLQ + алерт.

### Reaper и scheduled_actions

- Reaper (ShedLock, 30 с): `queued`/`in_progress` с `locked_until < now()` → `failed_retryable`.
- Автоудаление / открепление / первый комментарий - те же publisher’ы через очереди `actions.<network>`, идемпотентно.

### Гарантии

| Свойство | Как достигнуто |
|---|---|
| At-least-once | RabbitMQ + reaper + ретраи в PG |
| Нет дублей в сети | `idempotency_key` + `findExisting` + claim по state |
| Нет потери | Состояние только в PG; outbox для побочных событий |
| Изоляция сетей | Отдельные очереди и Deployment’ы `publisher-<network>` |

## Альтернативы

- **Отложенные сообщения / TTL в RabbitMQ как источник ретраев** - отвергнуто: состояние уходит из БД, сложнее наблюдаемость и reaper.
- **Kafka** - избыточен для точечных publish-команд; quorum RabbitMQ достаточен на 2–5k msg/с.
- **Только нативная отложка VK** - отвергнуто для MVP (единая логика статусов/ретраев); опция «страховки» - отдельный ADR фазы 2.
- **Один publisher на все сети** - отвергнуто: отказ VK не должен тормозить Telegram.

## Последствия

- Прототип конвейера - задача BE-4; **прод-качество реализовано в BE-10** (см. ADR-0010): Bucket4j AIMD, Bulkhead, `scheduled_actions` / `actions.*`, outbox, хуки BE-15/16/17, метрики под 04-devops §6.
- Партиционирование `publications` по `run_at` (месяцы) обязательно до нагрузки MVP.
- Метрики: `posts_publication_delay_seconds`, `posts_publications_total{network,result}`, retries/DLQ/throttling/scheduler; алерты на DLQ и рост `failed_retryable`.
- Топология брокера - ADR-0005.
- Нативная отложка VK (§5.6) - не в MVP; флаг `posts.publisher.vk.native-deferred.enabled` (ADR-0010).
