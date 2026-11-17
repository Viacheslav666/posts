# Runbook: прототип публикационного конвейера (BE-4)

## Компоненты

| Сервис | Порт (local) | Env |
|---|---|---|
| `posts-scheduler` | 8081 | Postgres, Redis (ShedLock), RabbitMQ |
| `posts-publisher` (`POSTS_PUBLISHER_NETWORK=tg`) | 8082 | Postgres, Redis (Bucket4j), RabbitMQ, social-emulator |
| `social-emulator` | 8090 | — |

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

- `posts_publication_delay_seconds` — задержка `published_at - run_at`
- `posts_publications_total{network,result}` — счётчик исходов
- `posts_ratelimit_throttled_total{network,level}` — отказы Bucket4j

## Алерты (минимум для прототипа)

- Рост `failed_retryable` / появление сообщений в `publish.*.dlq`
- CircuitBreaker `social-tg` в состоянии open
- Reaper переводит > N задач за 5 минут

## Интеграционные тесты

```bash
cd backend
./gradlew :apps:publisher:integrationTest
```

Покрывают TC-PUB-02/03/06/07 и нагрузку 10 000 публикаций против эмулятора.
