# Social Emulator (QA-3)

Лёгкий HTTP-эмулятор API соцсетей для e2e, нагрузки и хаос-тестов конвейера публикаций Posts.
Цель — проверять **0 потерь и 0 дублей** при ошибках, таймаутах и «успехе без ответа».

Стек: **Java 21 + Spring Boot 3.4**, порт **8090**.

## Быстрый старт

```bash
cd tests/social-emulator
./gradlew bootRun
# health
curl -s http://localhost:8090/_control/health
```

Сборка локального Docker-образа (Jib, соглашения DEV-3: distroless Java 21 nonroot, OCI labels, `IMAGE_TAGS`/`IMAGE_NAME`):

```bash
cd tests/social-emulator
./gradlew jibDockerBuild
# образ: ghcr.io/viacheslav666/posts-social-emulator:latest
# HTTP-probe с хоста (в образе нет shell): curl -s localhost:8090/_control/health
```

В docker-compose (профиль `emulator`):

```bash
docker compose -f infra/docker-compose/docker-compose.yml --profile emulator up -d social-emulator
```

## Эндпоинты сетей

| Сеть | Пути | Заметки |
|---|---|---|
| VK | `GET/POST /method/{method}`, `/vk/method/{method}` | `wall.post` (идемпотентность по `guid`), `photos.getWallUploadServer`, upload `/vk/upload/{id}`, `photos.saveWallPhoto` |
| Telegram | `POST /bot{token}/sendMessage\|sendPhoto\|sendMediaGroup` | также `/telegram/bot{token}/…`; 429 + `retry_after` |
| OK | `POST /ok/fb.do?method=mediatopic.post`, `/ok/api/mediatopic/post` | базовая публикация |
| Max | `POST /max/messages`, `/messages` | базовая публикация |

Успешный VK `wall.post` с тем же `owner_id` + `guid` возвращает тот же `post_id` и `post_already_exists=1` (без второго поста в store).

## Управляющий API (`/_control`)

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/_control/health` | liveness |
| GET/PUT | `/_control/scenario` | чтение/запись сценария ошибок |
| POST | `/_control/reset?scenario=true` | очистка постов (+ сброс сценария) |
| GET | `/_control/posts?network=&channelId=` | список созданных постов |
| GET | `/_control/posts/count` | счётчики по сетям |
| GET | `/_control/duplicates` | попытки повторов + true-duplicates |
| GET | `/_control/verify` | сверка: `ok=true` если нет true-duplicates |
| GET | `/_control/stats` | counts + scenario |

### Сценарий (пример)

```bash
curl -s -X PUT http://localhost:8090/_control/scenario \
  -H 'content-type: application/json' \
  -d '{
    "globalDelayMs": 0,
    "networks": {
      "vk": {"forcedError": "drop", "delayMs": 0},
      "telegram": {"forcedError": "429", "retryAfterSeconds": 5},
      "ok": {"errorRate": 0.1},
      "max": {"forcedError": "5xx", "httpStatus": 503}
    }
  }'
```

Значения `forcedError`:

| Значение | Поведение |
|---|---|
| `6` / `9` / `10` / `15` / `100` | ответ VK с `error.error_code` (без создания поста) |
| `429` | HTTP 429 + `Retry-After` / `retry_after` |
| `5xx` | HTTP 5xx (`httpStatus` или 503) |
| `drop` / `success_without_response` | пост **создаётся**, соединение обрывается (статус 499) |
| `hang` / `timeout` | долгая пауза `hangMs`, затем обрыв **без** создания поста |

Поля сети: `delayMs`, `errorRate` (0..1), `forcedError`, `hangMs`, `retryAfterSeconds`, `httpStatus`.

## Тесты

```bash
cd tests/social-emulator
./gradlew test spotlessCheck
```

## Связь с продуктом

Используется в TC-PUB-* (`docs/specs/05-qa.md`), прототипе конвейера BE-4 и compose-профиле DEV-4.
Адаптеры бэкенда должны указывать base URL эмулятора, например `http://localhost:8090`.
