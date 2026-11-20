# Локальный полный стек (DEV-4, profile `full`)

## Назначение

Воспроизводимое окружение для e2e и отладки публикационного конвейера: инфраструктура + все backend-приложения из локальных образов + Next.js web + эмулятор соцсетей.

## Быстрый старт

```bash
make up-full
```

Команда:

1. Собирает образы (`make images`: Jib apps + Dockerfile web + social-emulator).
2. Поднимает `docker compose --profile full`.
3. Ждёт `GET /api/v1/ping`, `GET /health` (через nginx `gateway` на :3000) и health эмулятора.

Повтор без пересборки:

```bash
docker compose -f infra/docker-compose/docker-compose.yml --profile full up -d
make wait-full
```

Остановка: `make down`.

## Внешние API → social-emulator (profile `full`)

Якорь compose `x-full-external-emulator-deps` подмешивается в `x-spring-local-deps`: все JVM-сервисы в profile `full` ходят в `http://social-emulator:8090`, а не в продакшен-хосты. Дополнительно `x-jvm-full-service` задаёт `extra_hosts` (например `api.vk.com` → `127.0.0.1`), чтобы случайный egress не ушёл в интернет.

| Переменная | Назначение |
|---|---|
| `POSTS_SOCIAL_EMULATOR_URL` | эмулятор для publisher/ingest |
| `POSTS_SOCIAL_BASE_URL` / `POSTS_SOCIAL_*_BASE_URL` | API соцсетей |
| `POSTS_VK_API_BASE_URL`, `POSTS_VK_ID_BASE_URL` | VK API и VK ID |
| `POSTS_YANDEX_OAUTH_*` | Yandex OAuth |
| `POSTS_YOOKASSA_BASE_URL` | YooKassa |
| `POSTS_ORD_VK_BASE_URL` | ОРД VK |

Сервисы `publisher-vk`, `publisher-tg`, `publisher-ok`, `publisher-max` — один образ `posts-publisher`, у каждого свой `POSTS_PUBLISHER_NETWORK` (`vk` / `tg` / `ok` / `max`).

## Порты приложений

| Сервис | Порт |
|---|---|
| api | 8080 |
| scheduler | 8081 |
| publisher-vk | 8082 |
| publisher-tg | 8083 |
| publisher-ok | 8084 |
| publisher-max | 8085 |
| media | 8086 |
| ingest | 8087 |
| notifications | 8088 |
| web (внутренний) | — (только из compose-сети) |
| gateway (браузер: web + `/api`, `/ws`) | 3000 |
| social-emulator | 8090 |

## Troubleshooting

- Distroless JVM-образы: healthcheck в compose только проверяет наличие Java; готовность API/web — с хоста (`make wait-full`).
- API стартует с `SPRING_PROFILES_ACTIVE=local`, datasource/redis/rabbit переопределены на имена сервисов compose.
- Если `make up-full` падает на ping: `docker compose -f infra/docker-compose/docker-compose.yml --profile full logs api --tail=100`.
- Если контейнеры не видят друг друга (timeout на `postgres:5432` при здоровом Postgres): на части окружений с Docker 29 + `bridge-nf-call-iptables=1` ICC режется правилами `DOCKER`. В `.cursor/environment.json` `start` выставляет `net.bridge.bridge-nf-call-iptables=0` (и ip6). Вручную: `sudo sysctl -w net.bridge.bridge-nf-call-iptables=0`.
