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
3. Ждёт `GET /api/v1/ping`, `GET /health` (web) и health эмулятора.

Повтор без пересборки:

```bash
docker compose -f infra/docker-compose/docker-compose.yml --profile full up -d
make wait-full
```

Остановка: `make down`.

## Конфиг публикатора → эмулятор

Сервисы `publisher-vk`, `publisher-tg`, `publisher-ok`, `publisher-max` используют один образ `posts-publisher` и различаются env:

| Переменная | Значение в compose |
|---|---|
| `POSTS_PUBLISHER_NETWORK` | `vk` / `tg` / `ok` / `max` |
| `POSTS_SOCIAL_BASE_URL` | `http://social-emulator:8090` |
| `SOCIAL_EMULATOR_URL` | `http://social-emulator:8090` |

Код `apps/scheduler` и `apps/publisher` в DEV-4 не меняется (реализация — BE-4).

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
| web | 3000 |
| social-emulator | 8090 |

## Troubleshooting

- Distroless JVM-образы: healthcheck в compose только проверяет наличие Java; готовность API/web — с хоста (`make wait-full`).
- API стартует с `SPRING_PROFILES_ACTIVE=local`, datasource/redis/rabbit переопределены на имена сервисов compose.
- Если `make up-full` падает на ping: `docker compose -f infra/docker-compose/docker-compose.yml --profile full logs api --tail=100`.
- Если контейнеры не видят друг друга (timeout на `postgres:5432` при здоровом Postgres): на части окружений с Docker 29 + `bridge-nf-call-iptables=1` ICC режется правилами `DOCKER`. В `.cursor/environment.json` `start` выставляет `net.bridge.bridge-nf-call-iptables=0` (и ip6). Вручную: `sudo sysctl -w net.bridge.bridge-nf-call-iptables=0`.
