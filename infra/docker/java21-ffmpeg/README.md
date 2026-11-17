# posts-java21-ffmpeg

Базовый образ для `apps/media`: Eclipse Temurin JRE 21 + `ffmpeg`, пользователь `65532` (nonroot).

```bash
docker build -t posts-java21-ffmpeg:21 infra/docker/java21-ffmpeg
```

Jib для media берёт его через `docker://posts-java21-ffmpeg:21` (локально) или
`ghcr.io/viacheslav666/posts-java21-ffmpeg:21` (CI после push в GHCR).
