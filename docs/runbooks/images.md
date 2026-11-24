# Сборка и публикация образов (DEV-3)

## Локально

### Backend (Jib → Docker daemon)

```bash
# База для media (JRE 21 + ffmpeg), один раз
docker build -t posts-java21-ffmpeg:21 infra/docker/java21-ffmpeg

cd backend
./gradlew jibDockerBuild
# или точечно:
./gradlew :apps:api:jibDockerBuild :apps:scheduler:jibDockerBuild
./gradlew :apps:media:jibDockerBuild -PpostsMediaBaseImage=docker://posts-java21-ffmpeg:21
```

Образы: `ghcr.io/viacheslav666/posts-<svc>:latest` (локальный тег).

Проверка health:

```bash
# Воркеры без внешних зависимостей
docker run --rm -d --name posts-scheduler -p 8081:8080 \
  ghcr.io/viacheslav666/posts-scheduler:latest
curl -sf http://127.0.0.1:8081/actuator/health
docker stop posts-scheduler

# API без БД - профиль openapi (только Actuator + springdoc)
docker run --rm -d --name posts-api -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=openapi \
  ghcr.io/viacheslav666/posts-api:latest
curl -sf http://127.0.0.1:8080/actuator/health
docker stop posts-api
```

### Frontend (Dockerfile)

```bash
docker build -f frontend/apps/web/Dockerfile -t posts-web:local frontend
docker run --rm -d --name posts-web -p 3000:3000 posts-web:local
curl -sf http://127.0.0.1:3000/health
docker stop posts-web
```

Или `make images` / `make images-run-smoke`.

## CI (`.github/workflows/images.yml`)

| Событие | Поведение |
|---|---|
| Pull request | Сборка всех образов, Trivy (fail на CRITICAL), SBOM CycloneDX - **без push** |
| Push в `main` / тег `v*` | Push в `ghcr.io/viacheslav666/posts-<svc>:<sha>` и `:<tag>`, SBOM, **cosign keyless**, Trivy |

Сервисы: `api`, `scheduler`, `publisher`, `media`, `ingest`, `notifications`, `web`.  
База media: `ghcr.io/viacheslav666/posts-java21-ffmpeg:21`.

## Переменные Jib

| Переменная / свойство | Назначение |
|---|---|
| `IMAGE_NAME` | Полное имя образа (по умолчанию `ghcr.io/viacheslav666/posts-<svc>`) |
| `IMAGE_TAGS` | Список тегов через запятую (по умолчанию `latest`) |
| `GITHUB_SHA` / `IMAGE_REVISION` | OCI label `org.opencontainers.image.revision` |
| `POSTS_MEDIA_BASE_IMAGE` / `-PpostsMediaBaseImage` | База для media (`docker://…` или registry ref) |

См. также [ADR-0009](../adr/0009-dev3-container-images.md).
