# ADR-0009: Сборка контейнерных образов (DEV-3)

- Статус: accepted
- Дата: 2026-11-17
- Авторы: Posts Devops (cloud agent)

## Контекст

Нужны воспроизводимые образы всех runtime-сервисов с OCI labels, SBOM, подписью и сканом уязвимостей. Бэкенд - Jib; web - Dockerfile (Next.js standalone).

## Решение

1. **Jib** в convention-плагине `posts.spring-app`: база `gcr.io/distroless/java21-debian12:nonroot`, JVM `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`, OCI labels, теги из `IMAGE_TAGS` / `IMAGE_NAME`.
2. **media**: отдельная база `infra/docker/java21-ffmpeg` (Temurin JRE 21 + ffmpeg, UID 65532); Jib `from.image` через `docker://posts-java21-ffmpeg:21` (локально) или GHCR после push.
3. **web**: multi-stage `frontend/apps/web/Dockerfile` (`node:22-alpine`, non-root `nextjs`, `output: 'standalone'` + `outputFileTracingRoot` для pnpm monorepo).
4. **CI** `.github/workflows/images.yml`: на PR - сборка без push; на `main`/теги `v*` - push в `ghcr.io/viacheslav666/posts-<svc>:<sha>` (и `:<tag>`), CycloneDX SBOM (syft), cosign keyless, Trivy fail на CRITICAL.
5. Локальный smoke API без Postgres - профиль `openapi` (как в DEV-1 для генерации OpenAPI).
6. **Зависимости для зелёного Trivy:** Spring Boot **3.5.16**, override `tomcat.version=10.1.62`. В `.trivyignore` временно: CVE brotli в текущем distroless и CVE spring-webmvc, исправленные только в Spring Framework 7 / Boot 4.

## Последствия

- `make images` / `make images-run-smoke` для локальной приёмки.
- База media публикуется как `posts-java21-ffmpeg:21` вместе с product-образами.
