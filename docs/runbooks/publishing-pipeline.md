# Runbook: публикационный конвейер (BE-10)

Заменяет прототипный `publishing-pipeline-prototype.md` для прод-контура.

## Компоненты

| Сервис | Порт (local) | Env |
|---|---|---|
| `posts-scheduler` | 8081 | Postgres, Redis (ShedLock), RabbitMQ |
| `posts-publisher` (`POSTS_PUBLISHER_NETWORK`) | 8082+ | Postgres, Redis (Bucket4j), RabbitMQ, emulator |
| Очереди | — | `publish.<network>`, `actions.<network>` + DLQ |

## Локальный прогон

```bash
make up
cd tests/social-emulator && ./gradlew bootRun &
cd backend
./gradlew :apps:scheduler:bootRun &
POSTS_PUBLISHER_NETWORK=tg POSTS_SOCIAL_EMULATOR_URL=http://localhost:8090 \
  ./gradlew :apps:publisher:bootRun
```

Для изоляции сетей поднимите отдельные процессы publisher с `POSTS_PUBLISHER_NETWORK=vk|ok|max`.

## Метрики (04-devops §6)

| Метрика | Алерт |
|---|---|
| `posts_publication_delay_seconds` (p99) | PublishLatencyHigh > 60 с |
| `posts_publications_total{result}` | PublishErrorRate |
| RabbitMQ queue depth `publish.*` | QueueBacklog |
| `posts_pipeline_dlq_total` / DLQ depth | DLQNotEmpty |
| `posts_scheduler_dispatched_total` / age | SchedulerStalled |
| Resilience4j CB state | CircuitBreakerOpen |
| `posts_ratelimit_throttled_total` | throttling / flood |
| Channel `reauth_required` spike | ChannelReauthSpike |

## Восстановление

1. **Очередь потеряна / RabbitMQ down:** PG — источник истины. После восстановления scheduler заново заберёт `pending`/`failed_retryable` (и reaper вернёт застрявшие `queued`/`in_progress`).
2. **DLQ не пуст:** разбор poison pill; при необходимости вернуть publication в `failed_retryable` с `next_attempt_at=now()`.
3. **Массовый reauth:** токены сети отозваны — чинить OAuth каналов; публикации в `failed_permanent` / `waiting_for_channel`.
4. **Redis down:** лимитер уходит в local fallback (консервативно) + алерт degradation.

## Тесты

```bash
cd backend
./gradlew :apps:publisher:integrationTest
./gradlew :apps:publisher:integrationTest -PincludeNightly=true   # + TC-PUB-01 10k
```

Матрица: `docs/qa/traceability-tc-pub.md`.
