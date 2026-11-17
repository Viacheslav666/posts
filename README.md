# Posts

SMM-платформа отложенного постинга (аналог и конкурент [vposter.ru](https://vposter.ru)): планирование и гарантированная публикация в VK, Telegram, OK, Max и др., командная работа, аналитика.

Репозиторий: монорепо `Viacheslav666/posts`. Целевая нагрузка — 100 000 пользователей с запасом ×3.

## Структура

```
backend/     Java 21 + Spring Boot 3, Gradle Kotlin DSL multi-module
frontend/    Next.js 15 + TypeScript, pnpm workspaces
infra/       docker-compose, позже Terraform/Helm/Argo CD
tests/       e2e, load, social-emulator, chaos
docs/        analysis, specs, ADR, OpenAPI, product, qa, runbooks
```

Подробности: [`docs/specs/00-overview.md`](docs/specs/00-overview.md), задачи агентов — [`docs/specs/agent-tasks.md`](docs/specs/agent-tasks.md).

## Быстрый старт (≤ 10 минут)

Требования: Docker, JDK 21, Node 22, pnpm.

```bash
# 1. Инфраструктура (Postgres, Redis, RabbitMQ, MinIO, ClickHouse, Mailpit, Grafana LGTM)
make up

# 2. Backend API (профиль local)
make backend
# в другом терминале:
curl -s http://localhost:8080/api/v1/ping
# → {"status":"ok"}

# 3. Frontend
make frontend
# открыть http://localhost:3000
```

Полезные команды:

```bash
make test      # Gradle build+IT и Vitest
make openapi   # перегенерация docs/api/openapi.yaml + TS-клиента
make lint
make down
```

Backend отдельно:

```bash
cd backend
./gradlew build integrationTest
./gradlew :apps:api:bootRun --args='--spring.profiles.active=local'
```

Frontend отдельно:

```bash
pnpm --dir frontend install
pnpm --dir frontend -r test
pnpm --dir frontend --filter @posts/web dev
```

## Образы (DEV-3)

```bash
make media-base          # JRE 21 + ffmpeg для posts-media
make images              # jibDockerBuild всех apps/* + Dockerfile web
make images-run-smoke    # docker run → /actuator/health и /health
```

Реестр: `ghcr.io/viacheslav666/posts-<svc>:<sha>` (CI на `main`/тегах, cosign + SBOM + Trivy).  
Подробности: [`docs/runbooks/images.md`](docs/runbooks/images.md).

## Локальные порты

| Сервис | Порт |
|---|---|
| API | 8080 |
| Web | 3000 |
| Postgres | 5432 |
| Redis | 6379 |
| RabbitMQ / UI | 5672 / 15672 |
| S3 mock (adobe/s3mock) | 9000 |
| ClickHouse HTTP / native | 8123 / 9009 |
| Mailpit UI / SMTP | 8025 / 1025 |
| Grafana (otel-lgtm) | 3001 |
| Social emulator | 8090 |

Учётки по умолчанию — в [`.env.example`](.env.example).

Эмулятор соцсетей (QA-3): `make emulator` или `make up-emulator` (Jib-образ + compose profile `emulator`). Описание API — [`tests/social-emulator/README.md`](tests/social-emulator/README.md).

## Документация

- Анализ конкурента: [`docs/analysis.md`](docs/analysis.md)
- ТЗ по ролям: [`docs/specs/`](docs/specs/)
- ADR: [`docs/adr/`](docs/adr/)
- OpenAPI: [`docs/api/openapi.yaml`](docs/api/openapi.yaml)

## Лицензия

Proprietary — All rights reserved (см. `LICENSE`).
