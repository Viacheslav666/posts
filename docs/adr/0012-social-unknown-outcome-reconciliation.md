# ADR-0012: Сверка неизвестного исхода в реальных соцсетях

- Статус: accepted
- Дата: 2026-11-18
- Авторы: Posts Разраб Бэк (BE-23)
- Связано: ADR-0004, ADR-0010, `02-backend.md` §5.3 / §6, BE-23

## Контекст

Публикационный конвейер гарантирует **0 дублей** при at-least-once доставке. Если HTTP-вызов к соцсети оборвался после отправки запроса (таймаут, обрыв соединения → `ErrorClass.UNKNOWN_OUTCOME`), publisher вызывает `SocialAdapter.findExisting` перед повторной публикацией.

Прототип адаптеров VK/OK ходил в эмуляторный служебный API `GET /_control/posts` по `idempotencyKey`. В бою этого эндпоинта нет — сверка должна опираться на **официальный API** сети.

## Решение

### Режимы сверки (`ReconciliationMode`)

| Режим | Когда | Как |
|---|---|---|
| `emulator` | local / IT / `posts.social.{vk\|ok}.reconciliation-mode=emulator` | `GET /_control/posts` (пакет `..emulator..`) |
| `api` (default prod) | staging / production | официальные методы чтения стены / ленты |

`/_control` **запрещён** вне пакета `..emulator..` правилом ArchUnit (BE-23).

### VK (API 5.199)

1. **Сетевая защита:** `wall.post` всегда передаёт `guid` (= `idempotency_key` публикации). Повтор с тем же `guid` в окне VK (~1 ч) возвращает существующий `post_id` (`post_already_exists`).
2. **Сверка при UNKNOWN_OUTCOME:** `wall.get` (фильтры `all` и `postponed`, последние N постов) сравнивает:
   - невидимый маркер fingerprint (SHA-256 префикс ключа идемпотентности) в тексте, если он был в meta; и/или
   - точное совпадение текста + окно времени ±`findExistingTimeWindowSeconds` (default 120 с).
3. Истории: в prod без надёжного индекса возвращаем empty и полагаемся на `guid` при повторной загрузке; в emulator — `/_control`.

### OK

1. `mediatopic.post` с `idempotency_key` + JSON `attachment` (media blocks).
2. Сверка: `stream.get` / лента группы — поиск по тексту / fingerprint в окне времени.
3. Подпись запросов: `application_key` + `sig` (MD5) при наличии `applicationSecret`; для эмулятора secret пустой → sig не ставится.

### BE-33 `publish_suggested` (VK)

Для удалённого действия `publish_suggested` при `UNKNOWN_OUTCOME` (таймаут / обрыв после `wall.post`+`post_id`):

1. `RemoteActionProcessor` переводит строку в `unknown_outcome`.
2. Вызывает [`SocialModerationPort.reconcilePublishSuggested`](../../backend/libs/social-api/src/main/java/ru/posts/social/api/SocialModerationPort.java):
   - `wall.get` с `filter=suggests` — есть ли ещё предложенный пост;
   - `wall.getById` — появился ли уже опубликованный пост с тем же id.
3. Если сверка находит опубликованный исход → `succeeded` + запись в `remote_post_registry` (**0 дублей**, без повторного `wall.post`).
4. Если сверка пустая → `failed_retryable` с backoff и повторной попыткой.

### Publisher

- На `UNKNOWN_OUTCOME` публикации → `findExisting`; при успехе — `succeeded` без повторного `publish`.
- На `UNKNOWN_OUTCOME` для `publish_suggested` → `reconcilePublishSuggested` (см. выше).
- Медиа загружаются до `publish` с кэшем `channel_media_cache` (V17), чтобы ретрай не грузил файл заново.

## Альтернативы

1. **Только `guid` / идемпотентность сети** — недостаточно: после таймаута нельзя отличить «не дошло» от «дошло, ответа нет», если окно guid истекло.
2. **Хранить remote id в outbox до ack сети** — не закрывает случай обрыва после commit на стороне сети.
3. **Оставить `/_control` в prod** — невозможно вне эмулятора.

## Последствия

- Нужны права токена на чтение стены / ленты (`wall`, OK stream).
- Ложные промахи findExisting возможны при правках текста ОРД-маркером между попытками — fingerprint / точное сравнение текста после хука.
- Emulator расширен: `wall.post` зеркалит посты в `wall.get`; OK — `stream.get`, upload URL.
