# ADR-0010: Прод-качество публикационного конвейера (BE-10)

- Статус: accepted
- Дата: 2026-11-18
- Авторы: Posts Разраб Бэк (BE-10)
- Связано: ADR-0004, ADR-0005, `02-backend.md` §5 / §12 п.9

## Контекст

BE-4 дал прототип scheduler → RabbitMQ → publisher. BE-10 доводит контур до прод-качества: лимиты, resilience, scheduled_actions, идемпотентность end-to-end, метрики под алерты 04-devops §6, точки расширения для BE-15/16/17.

## Решение

### Rate limiting и resilience

- Bucket4j + Redis: ключи `rl:{network}:global|token|chat`; AIMD `penalize()` после 429/flood (VK flood → блок до 1 ч).
- Fallback при недоступности Redis - локальный Resilience4j RateLimiter + метрика `posts_ratelimit_redis_degraded_total`.
- Resilience4j: CircuitBreaker + TimeLimiter + Bulkhead на вызов сети. Повтор **публикации** - только через PG `next_attempt_at` (не Retry Resilience4j).

### scheduled_actions

- Топология `actions` / `actions.dlx` / `actions.<network>` (+ DLQ), тот же `publisher-<network>`.
- После `succeeded` материализуются `pin`, `unpin`, `first_comment`, `delete` / `delete_story` из `post_variants.options`.
- Диспетчер scheduler: SKIP LOCKED + publisher confirms; reaper общий с publications.

### Идемпотентность и outbox

- Claim по `state='queued'`, `idempotency_key`, `findExisting` / `remote_post_id`.
- Успех/permanent fail пишут outbox (`publication.succeeded` / `publication.failed`) для WS и уведомлений.
- AUTH → `channels.status=reauth_required`.

### Точки расширения (без реализации фич)

| Хук | Назначение |
|---|---|
| `PrePublishHook` | BE-15: erid/маркер, fail-closed без erid |
| `SocialAdapter.publishStory` / `deleteStory` | BE-16: `content_kind=story` в том же Deployment |
| `source_type` в outbox + единый schedule API | BE-17: публикация граббера только через конвейер |
| `posts.publisher.vk.native-deferred.enabled` | §5.6 нативная отложка VK - **выкл.**; Phase 2 |

### Метрики

`posts_publication_delay_seconds`, `posts_publications_total`, `posts_publication_retries_total`, `posts_ratelimit_throttled_total`, `posts_pipeline_dlq_total`, `posts_scheduled_actions_total`, `posts_scheduler_*`, состояние CB/Bulkhead (Resilience4j Micrometer). AMQP: заголовок `traceparent`.

## Альтернативы

- Ретраи через TTL/DLX RabbitMQ - отвергнуто (ADR-0004).
- Отдельный Deployment на stories - отвергнуто; ветвление по `content_kind`.

## Последствия

- Runbook: `docs/runbooks/publishing-pipeline.md`.
- TC-PUB-09/11/12/13 автоматизированы в CI (`TcPubBe10IT`); TC-PUB-10 остаётся staging/QA-8.
