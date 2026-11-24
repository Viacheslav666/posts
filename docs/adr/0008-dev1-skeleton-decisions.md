# ADR-0008: Решения каркаса DEV-1

- Статус: accepted
- Дата: 2026-11-17
- Авторы: Posts Devops (cloud agent)
- Примечание: ранее файл назывался `0001-dev1-skeleton-decisions.md`; перенумерован в BE-1, чтобы ADR-0001…0007 заняли архитектурный ряд из `02-backend.md` §12.

## Контекст

В ТЗ DEV-1 и документах `00/02/03/04` есть несколько мест, где детали расходятся или инструменты конфликтуют с текущими версиями toolchain.

## Решение

1. **Ветка:** по политике cloud-агента используется `cursor/chore-dev-1-repo-skeleton-da83` (вместо `chore/dev-1-repo-skeleton` из agent-tasks).
2. **Генерация OpenAPI:** вместо `springdoc-openapi-gradle-plugin` 1.9 (ломается на Gradle 8.12 multi-module jar deps) - кастомная задача `:apps:api:generateOpenApiDocs`, которая поднимает fat-jar с профилем `openapi` и сохраняет `/v3/api-docs.yaml` в `docs/api/openapi.yaml`.
3. **Профиль `openapi`:** без DataSource/JPA/Flyway - только веб + springdoc, чтобы генерация не зависела от Postgres.
4. **Testcontainers:** версия **1.21.4** (+ `api.version=1.44` в CI) из‑за Docker Engine 29 (min API 1.44).
5. **NullAway:** в DEV-1 не включаем (только Error Prone core) - полный NullAway откладываем; при необходимости отдельная задача после BE-1.
6. **ArchUnit:** в DEV-1 жил в `libs/architecture`; **BE-1 перенёс правила в `libs/test-support`** (см. ADR-0002) - модуль `architecture` удалён.
7. **`analytics` / `social-rutube`:** упомянуты в overview как фаза 2 - в DEV-1 не создаём (список stubs по agent-tasks).
8. **Configuration cache:** временно выключен в `backend/gradle.properties` из‑за задач генерации OpenAPI/Testcontainers.
9. **Локальный S3:** вместо MinIO - `adobe/s3mock` с бакетом `posts-media` (официальные образы `minio/minio` / `minio/mc` недоступны из публичных registry в момент DEV-1). Порт хоста `9000` → контейнер `9090`.

## Альтернативы

- Оставить springdoc gradle plugin 1.8/2.0 и чинить dependsOn - отвергнуто как хрупкое для скелета.
- H2 + Flyway для openapi - отвергнуто (TIMESTAMPTZ/PG SQL).
- Docker daemon `min-api-version: 1.32` - хуже, чем апгрейд Testcontainers.

## Последствия

- `make openapi` воспроизводим локально и в CI job `contract`.
- Каркасные отклонения DEV-1 остаются в силе, пока не superseded отдельным ADR.
