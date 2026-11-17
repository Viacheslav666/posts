# Base image for posts-media: JRE 21 + ffmpeg (Jib from-image).
# Build: docker build -t posts-java21-ffmpeg:21 infra/docker/java21-ffmpeg
FROM eclipse-temurin:21.0.6_7-jre-jammy

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ffmpeg \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 65532 nonroot \
    && useradd --uid 65532 --gid 65532 --home-dir /home/nonroot --create-home \
        --shell /usr/sbin/nologin nonroot

# Verify ffmpeg is on PATH for media workers.
RUN ffmpeg -version | head -n 1

USER 65532:65532
