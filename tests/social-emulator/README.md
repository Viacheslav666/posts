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

Полный стек с приложениями и web (DEV-4, профиль `full`): `make up-full` — эмулятор поднимается вместе с api/scheduler/publisher-*/media/ingest/notifications/web; публикаторы смотрят на `http://social-emulator:8090`.

## Эндпоинты сетей

| Сеть | Пути | Заметки |
|---|---|---|
| VK | `GET/POST /method/{method}`, `/vk/method/{method}` | `wall.post` (идемпотентность по `guid`), `wall.get` (фикстуры источников граббера), `photos.getWallUploadServer`, upload `/vk/upload/{id}`, `photos.saveWallPhoto` |
| VK stories | `stories.getPhotoUploadServer` / `stories.getVideoUploadServer` → upload → `stories.save` / `stories.delete` | metadata `contentType=story`; идемпотентность по `guid`; `upload_url` из `posts.emulator.public-base-url` |
| Telegram | `POST /bot{token}/sendMessage\|sendPhoto\|sendMediaGroup` | также `/telegram/bot{token}/…`; 429 + `retry_after` |
| OK | `POST /ok/fb.do?method=mediatopic.post`, `/ok/api/mediatopic/post` | базовая публикация |
| OK Moments | `moments.getUploadUrl` → `/ok/upload/{id}` → `moments.commit` / `moments.delete` | `owner_ref=group:…`; metadata `contentType=story` |
| Max | `POST /max/messages`, `/messages` | базовая публикация |
| VK moderation (BE-33) | `wall.delete`, `*.deleteComment`/`restoreComment`, `groups.ban`/`unban`, `messages.send`, `execute`, `wall.post(post_id)`, `wall.get(filter=suggests)`, `wall.getById` | in-memory suggests/comments |
| Telegram moderation | `deleteMessage`/`deleteMessages` (окно 48 ч), `banChatMember`, `unbanChatMember`, `restrictChatMember`, `getChat` | `linked_chat_id` |
| Max moderation | `DELETE /messages?message_id=`, `DELETE …/comments`, `POST …/comments`, `DELETE /chats/{id}/members` | restore → `not.supported` |

Успешный VK `wall.post` с тем же `owner_id` + `guid` возвращает тот же `post_id` и `post_already_exists=1` (без второго поста в store).  
То же для `stories.save` / `moments.commit` по `guid` / `idempotency_key`.

## Управляющий API (`/_control`)

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/_control/health` | liveness |
| GET/PUT | `/_control/scenario` | чтение/запись сценария ошибок |
| POST | `/_control/reset?scenario=true` | очистка постов (+ сброс сценария) |
| GET | `/_control/posts?network=&channelId=&contentType=` | список созданных постов; `contentType=story` — только истории/Moments |
| GET | `/_control/posts/count` | счётчики по сетям |
| GET | `/_control/duplicates` | попытки повторов + true-duplicates |
| GET | `/_control/verify` | сверка: `ok=true` если нет true-duplicates |
| GET | `/_control/stats` | counts + scenario |
| PUT | `/_control/vk/wall/{ownerId}` | QA-12: seed `wall.get` items `{"items":[{"id":1,"text":"..."}]}` |
| POST | `/_control/vk/wall/{ownerId}/posts` | добавить один пост в wall-источник |
| GET | `/_control/vk/wall` | снимок всех seeded walls |
| POST | `/_control/vk/story-tokens/{groupId}/revoke` | QA-11 / TC-STORY-20: отзыв stories-токена (далее `stories.*` → error_code=5) |
| POST | `/_control/vk/story-tokens/{groupId}/restore` | восстановить stories-токен |
| GET | `/_control/vk/story-tokens` | список отозванных group id |
| PUT | `/_control/vk/suggests` | BE-33: seed предложенных записей VK |
| PUT | `/_control/comments` | BE-33: seed комментариев (deleted/restored) |
| PUT | `/_control/posts` | seed постов с `createdAt` (окно удаления TG 48 ч) |
| GET | `/_control/comments`, `/_control/bans`, `/_control/vk/suggests` | снимки moderation store |

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
| `5` / `6` / `9` / `10` / `15` / `27` / `203` / `100` | ответ VK с `error.error_code` (без создания поста); `5` = auth failed; на TG/OK/Max → HTTP 403 / Max `access.denied` |
| `access.denied` / `access_denied` | Max: HTTP 403 + `code=access.denied`; VK → error 15 |
| `429` | HTTP 429 + `Retry-After` / `retry_after` |
| `5xx` | HTTP 5xx (`httpStatus` или 503) |
| `403` / `401` / `permanent` / `forbidden` / `no_rights` | HTTP 4xx без создания поста (по умолчанию 403) |
| `drop` / `success_without_response` | пост **создаётся**, соединение обрывается (статус 499) |
| `hang` / `timeout` | долгая пауза `hangMs`, затем обрыв **без** создания поста |

Поля сети: `delayMs`, `errorRate` (0..1), `forcedError`, `hangMs`, `retryAfterSeconds`, `httpStatus`.

Для pipeline IT с ephemeral-портом задайте `posts.emulator.public-base-url=http://127.0.0.1:<port>` (SocialEmulatorProcess делает это автоматически), иначе `upload_url` в stories/moments укажет на `:8090`.

## Тесты

```bash
cd tests/social-emulator
./gradlew test spotlessCheck
```

## Связь с продуктом

Используется в TC-PUB-* (`docs/specs/05-qa.md`), прототипе конвейера BE-4 и compose-профиле DEV-4.
Адаптеры бэкенда должны указывать base URL эмулятора, например `http://localhost:8090`.
