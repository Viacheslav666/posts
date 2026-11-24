# Runbook: прототип публикационного конвейера (BE-4)

> **Актуально для прода:** см. [`publishing-pipeline.md`](publishing-pipeline.md) (BE-10).

## Компоненты

| Сервис | Порт (local) | Env |
|---|---|---|
| `posts-scheduler` | 8081 | Postgres, Redis (ShedLock), RabbitMQ |
| `posts-publisher` (`POSTS_PUBLISHER_NETWORK=tg`) | 8082 | Postgres, Redis (Bucket4j), RabbitMQ, social-emulator |
| `social-emulator` | 8090 | - |

## Локальный прогон

```bash
make up
cd tests/social-emulator && ./gradlew bootRun &
cd backend
./gradlew :apps:scheduler:bootRun &
POSTS_PUBLISHER_NETWORK=tg POSTS_SOCIAL_EMULATOR_URL=http://localhost:8090 \
  ./gradlew :apps:publisher:bootRun
```

## Метрики

- `posts_publication_delay_seconds` - задержка `published_at - run_at`
- `posts_publications_total{network,result}` - счётчик исходов
- `posts_ratelimit_throttled_total{network,level}` - отказы Bucket4j

## Алерты (минимум для прототипа)

- Рост `failed_retryable` / появление сообщений в `publish.*.dlq`
- CircuitBreaker `social-tg` в состоянии open
- Reaper переводит > N задач за 5 минут

## Интеграционные тесты (QA-5)

```bash
cd backend
# CI: быстрый набор TC-PUB (без @Tag("nightly"))
./gradlew :apps:publisher:integrationTest

# Nightly: + TC-PUB-01 (10k, p99 < 60s)
./gradlew :apps:publisher:integrationTest -PincludeNightly=true
```

Матрица кейс → тест: `docs/qa/traceability-tc-pub.md`.  
Покрывают TC-PUB-01…08 (реализуемые на прототипе TG), rate-limit, CB, deferred/cancel; TC-PUB-09…13 - `@Disabled` «ожидает BE-10».
