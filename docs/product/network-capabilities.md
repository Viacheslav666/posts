# Матрица возможностей сетей (NetworkCapabilities)

| Поле | Значение |
|---|---|
| Версия | 1.2.1 |
| Статус | Готово к использованию бэком (задача **AN-2**; inbound **BE-32**; модерация **BE-33**; предложка **BE-34**) |
| Дата проверки | **2026-11-19** |
| Машиночитаемый источник | [`docs/api/fixtures/network-capabilities.yaml`](../api/fixtures/network-capabilities.yaml) |
| JSON Schema | [`docs/api/fixtures/network-capabilities.schema.json`](../api/fixtures/network-capabilities.schema.json) |
| Валидация | `python3 docs/api/fixtures/validate-network-capabilities.py` |

> Правило AN-2: **каждое** числовое/булево значение — со ссылкой на **официальную** документацию и датой проверки. Если официальный источник не публикует цифру — в YAML `status: unknown`, `value: null`. Не подставлять «народные» лимиты в прод-валидацию без решения владельца.

**Потребители:**
- Бэк: `NetworkCapabilities` + `posts.ratelimit.<network>.*` (Bucket4j), `SocialAdapter.validate`.
- Фронт: `GET /api/v1/networks/capabilities` (счётчики, блокировка сохранения).
- Контент-рендер: режим форматирования (`plain` / `html`).

---

## 1. Сводная матрица (MVP + Rutube)

| Возможность | VK | Telegram | OK | Max | Rutube |
|---|---|---|---|---|---|
| Фаза продукта | MVP | MVP | MVP | MVP | Фаза 2 |
| Текст (max chars) | **unknown** | **4096** | **unknown** (есть error 601) | **4000** | title **100** / desc **5000** |
| Форматирование | plain | HTML | plain | HTML/Markdown | plain |
| Медиа в посте | ≤ **10** (фото+видео) | альбом **2–10** | unknown | ≤ **12** вложений | **1** видео |
| Закреп / откреп | да / да | да / да | unknown | да / да | нет |
| Удаление после публикации | да | да | да | да | да |
| Первый комментарий | да (`wall.createComment`) | да (discussion) | unknown | unknown | unknown |
| Истории / моменты | да (Фаза 2) | нет (Bot API) | да, Moments (Фаза 2) | unknown | нет |
| Нативный schedule | `publish_date` | нет | `publishAt` | unknown | unknown |
| Inline-кнопки | нет (стена) | да | нет | да | нет |
| RPS (офиц.) | user **3**/с, community **20**/с | ~**30**/с bulk; **1**/с chat; **20**/мин group | не опубликован | **30** rps; **2**/с на чат | не опубликован |
| Токен | VK ID AT **1 ч**, RT **180 д** | bot token (TTL unknown) | LONG **30 д** | bot token (TTL unknown) | JWT AT **14 д** (`expires_in=1209600`) |

---

## 2. VK

**Доки:** [dev.vk.ru](https://dev.vk.ru/) · [wall.post](https://dev.vk.ru/ru/method/wall.post) · [формат запросов / лимиты](https://dev.vk.ru/ru/api/api-requests) · [загрузка фото на стену](https://dev.vk.ru/ru/api/upload/wall-photo) · [истории](https://dev.vk.ru/ru/api/upload/story-in-profile) · [VK ID токены](https://id.vk.ru/about/business/go/docs/ru/vkid/latest/vk-id/connection/tokens/about) · [Callback API](https://dev.vk.ru/ru/api/callback/getting-started)

| Параметр | Значение | Источник | Проверено |
|---|---|---|---|
| Текст поста | unknown (число не в схеме `wall.post`) | wall.post | 2026-11-17 |
| Медиа в посте | ≤ 10 (фото, видео) | wall.post | 2026-11-17 |
| Фото: форматы / размер | JPG, PNG, GIF; ≤ 50 МБ; сумма сторон ≤ 14 000 px; ratio ≥ 1:20 | upload wall-photo | 2026-11-17 |
| Документ | 1 файл (`doc`) | wall.post | 2026-11-17 |
| Аудио | до 10 треков или 1 плейлист | wall.post | 2026-11-17 |
| Форматирование | plain | wall.post | 2026-11-17 |
| Нативная отложка | `publish_date` | wall.post | 2026-11-17 |
| Редактирование / удаление | `wall.edit` / `wall.delete` | методы wall.* | 2026-11-17 |
| Закреп / откреп | `wall.pin` / `wall.unpin` | методы wall.* | 2026-11-17 |
| Первый комментарий | `wall.createComment` | wall.createComment | 2026-11-17 |
| Закрыть комментарии / mute | `close_comments`, `mute_notifications` | wall.post | 2026-11-17 |
| Карусель/сетка | `primary_attachments_mode` | wall.post | 2026-11-17 |
| Идемпотентность | `guid` (1 час) | wall.post | 2026-11-17 |
| Истории | да; фото JPG/PNG/GIF/WEBP/HEIF; видео ≤ 350 МБ | story-in-profile | 2026-11-17 |
| RPS | user 3/с; community 20/с; error 6 | api-requests | 2026-11-17 |
| Публикаций/сутки | unknown (количественные лимиты не публикуют) | api-requests | 2026-11-17 |
| Access / Refresh TTL | 3600 с / 15 552 000 с (180 д) | VK ID docs | 2026-11-17 |
| Webhooks | Callback API | callback getting-started | 2026-11-17 |

**Конфиг лимитера (рекомендация):** `posts.ratelimit.vk.user_token_rps=3`, `community_token_rps=20`.

---

## 3. Telegram (Bot API)

**Доки:** [Bot API](https://core.telegram.org/bots/api) · [FAQ лимиты](https://core.telegram.org/bots/faq) · [Formatting](https://core.telegram.org/bots/api#formatting-options) · [sendMessage](https://core.telegram.org/bots/api#sendmessage) · [sendMediaGroup](https://core.telegram.org/bots/api#sendmediagroup)

| Параметр | Значение | Источник | Проверено |
|---|---|---|---|
| Текст | 1–4096 символов после entities | sendMessage | 2026-11-17 |
| Caption | 0–1024 | sendPhoto / sendVideo | 2026-11-17 |
| Альбом | 2–10 | sendMediaGroup | 2026-11-17 |
| Фото | ≤ 10 MB; width+height ≤ 10000 | sendPhoto | 2026-11-17 |
| Видео / файл | до 50 MB | sendVideo / FAQ | 2026-11-17 |
| Форматирование | HTML (`b,i,u,s,span,a,code,pre,blockquote`, spoiler…) | formatting options | 2026-11-17 |
| Нативная отложка | нет | Bot API | 2026-11-17 |
| Edit / delete / pin | editMessage*, deleteMessage, pinChatMessage | Bot API | 2026-11-17 |
| Первый комментарий | через discussion-группу канала | sendMessage | 2026-11-17 |
| Истории | нет в Bot API как постинг | Bot API | 2026-11-17 |
| Кнопки | InlineKeyboardMarkup | Bot API | 2026-11-17 |
| RPS | ~30/с bulk; ~1/с на чат; 20/мин на группу | FAQ | 2026-11-17 |
| Токен | bot token; TTL числом не опубликован | BotFather / tutorial | 2026-11-17 |
| Webhooks | `setWebhook` + `secret_token` | setWebhook | 2026-11-17 |
| Права бота | `can_post_messages`, `can_edit_messages`, `can_delete_messages`, `can_pin_messages` | ChatAdministratorRights | 2026-11-17 |

**Конфиг:** `posts.ratelimit.telegram.global_msg_rps=30`, `per_chat_msg_rps=1`, `per_group_msg_rpm=20`.

---

## 4. Одноклассники (OK)

**Доки:** [apiok.ru](https://apiok.ru/) · [mediatopic.post](https://apiok.ru/dev/methods/rest/mediatopic/mediatopic.post) · [ошибки](https://apiok.ru/dev/errors) · [limits](https://apiok.ru/dev/limits) · [permissions / LONG_ACCESS_TOKEN](https://apiok.ru/ext/oauth/permissions) · [moments](https://apiok.ru/dev/methods/rest/moments/) · [deleteTopic](https://apiok.ru/dev/methods/rest/mediatopic/mediatopic.deleteTopic)

| Параметр | Значение | Источник | Проверено |
|---|---|---|---|
| Текст | unknown; есть `MEDIA_TOPIC_TEXT_LIMIT` (601) без числа | mediatopic.post / errors | 2026-11-17 |
| Медиа-блоки | text, photo, movie, music, poll, link, reshare… | mediatopic.post | 2026-11-17 |
| Кол-во фото/видео | unknown (`MEDIA_TOPIC_BLOCK_LIMIT`=600 без порога) | errors | 2026-11-17 |
| Форматирование | plain | mediatopic.post | 2026-11-17 |
| Нативная отложка | `publishAt` / `publishAtMs` | mediatopic.post | 2026-11-17 |
| Удаление | `mediatopic.deleteTopic` | deleteTopic | 2026-11-17 |
| Закреп | unknown | список mediatopic | 2026-11-17 |
| Первый комментарий | unknown | — | 2026-11-17 |
| Моменты (истории) | `moments.*` | moments | 2026-11-17 |
| disableComments | да (групповые темы) | mediatopic.post | 2026-11-17 |
| RPS | не опубликован («почти нет ограничений») | limits | 2026-11-17 |
| Токен | LONG_ACCESS_TOKEN **30 дней**, автопродление | oauth permissions | 2026-11-17 |
| Webhooks | unknown | — | 2026-11-17 |

**Конфиг (защита Posts, не лимит OK):** `posts.ratelimit.ok.token_rps=5`.

---

## 5. Max

**Доки:** [dev.max.ru/docs-api](https://dev.max.ru/docs-api) · [POST /messages](https://dev.max.ru/docs-api/methods/POST/messages) · [форматирование](https://dev.max.ru/docs-api/use-cases/sending-messages/text-formatting) · [uploads](https://dev.max.ru/docs-api/methods/POST/uploads) · [prepare / 30 rps](https://dev.max.ru/docs/chatbots/bots-coding/prepare) · [subscriptions](https://dev.max.ru/docs-api/methods/POST/subscriptions)

| Параметр | Значение | Источник | Проверено |
|---|---|---|---|
| Текст | до 4000 | POST /messages | 2026-11-17 |
| Вложения | ≤ 12 | POST /messages | 2026-11-17 |
| image | JPG/JPEG/PNG/GIF/TIFF/BMP/HEIC; ≤ 50 МБ; ≤ 7680×7680 | POST /uploads | 2026-11-17 |
| video | MP4/MOV/MKV/WEBM; ≤ 250 МБ | POST /uploads | 2026-11-17 |
| audio | ≤ 256 МБ и ≤ 60 мин | POST /uploads | 2026-11-17 |
| file | до 4 ГБ | POST /uploads | 2026-11-17 |
| Форматирование | `html` / `markdown` (b/i/u/s/code/a/h*/blockquote/mark…) | text-formatting | 2026-11-17 |
| Edit / delete | PUT/DELETE /messages (≤2/с на чат) | docs-api | 2026-11-17 |
| Pin / unpin | PUT/DELETE `/chats/{chatId}/pin` | docs-api | 2026-11-17 |
| Первый комментарий | unknown | — | 2026-11-17 |
| Истории | unknown | — | 2026-11-17 |
| notify | `notify` (каналы — без silent) | NewMessageBody | 2026-11-17 |
| Кнопки | inline_keyboard (≤7 в ряду, ≤30 рядов) | POST /messages | 2026-11-17 |
| RPS | 30 глобально; 2 msg/с на чат | prepare / POST messages | 2026-11-17 |
| Токен | bot access_token; TTL unknown | docs-api | 2026-11-17 |
| Webhooks | POST /subscriptions, HTTPS, secret | subscriptions | 2026-11-17 |

**Конфиг:** `posts.ratelimit.max.global_rps=30`, `per_chat_msg_rps=2`. Базовый URL: `https://platform-api2.max.ru/`.

---

## 6. Rutube (Фаза 2)

**Доки:** [PartnerAPI r2b.su](https://r2b.su/) · [FAQ загрузки](https://rutube.ru/info/upload/)

| Параметр | Значение | Источник | Проверено |
|---|---|---|---|
| Title | ≤ 100 символов | r2b.su | 2026-11-17 |
| Description | ≤ 5000 символов | r2b.su | 2026-11-17 |
| Видео | ≤ 25 Гб; ≤ 5 ч; широкий список контейнеров | FAQ upload | 2026-11-17 |
| Обложка | jpeg/jpg/png; ≤ 10 МБ; ≤ 1920×1080 | FAQ upload | 2026-11-17 |
| Параллельные загрузки | до 5 | FAQ upload | 2026-11-17 |
| Edit / delete | PATCH / DELETE video | r2b.su | 2026-11-17 |
| Pin / stories / кнопки | нет | r2b.su | 2026-11-17 |
| Нативный schedule | unknown в PartnerAPI | r2b.su | 2026-11-17 |
| RPS | unknown | r2b.su | 2026-11-17 |
| Access token TTL | 1 209 600 с (14 суток) по `expires_in` в примере | r2b.su | 2026-11-17 |
| Auth | partner code → JWT access/refresh | r2b.su | 2026-11-17 |
| Callback | `callback_url` / `errback_url` при upload | r2b.su | 2026-11-17 |

**Конфиг:** `posts.ratelimit.rutube.token_rps=5`, `max_parallel_uploads=5`.

---

## 7. Как бэкенду читать YAML

1. Десериализовать `networks[]` → `Map<NetworkId, NetworkCapabilities>`.
2. Для валидации UI/API брать только `status: confirmed`; `unknown` → не жёсткий fail (или warn + feature flag).
3. Rate limit: поля `api_limits.config_keys` → `posts.ratelimit.<id>.*`.
4. Форматирование: `formatting.mode` + `formatting.features` / `html_tags` → `libs/content` + фикстуры `docs/api/fixtures/formatting/`.
5. Перепроверка: при смене лимитов сети обновить YAML + `checked_at`, прогнать `validate-network-capabilities.py`.

### Проверка схемы

```bash
pip install pyyaml jsonschema
python3 docs/api/fixtures/validate-network-capabilities.py
```

---

## 8. Модерация и события (BE-33)

Удалённые действия модерации / suggest идут через `SocialModerationPort` → очередь `social_remote_actions` → `actions.moderation.<network>`. Ниже — официальные методы и тип ключа доступа. **checked_at: 2026-11-18**.

### VK (user vs community)

| Действие | Метод(ы) | Ключ | Примечание |
|---|---|---|---|
| `publish_suggested` | `wall.post` + `post_id` | **user** | публикация предложенного на стену сообщества; **`post_id` принимает `message`/`attachments`** (правка текста при одобрении) — проверено по [wall.post](https://dev.vk.ru/ru/method/wall.post) **2026-11-19** (BE-34) |
| `delete_post` | `wall.delete` | **user** | |
| `delete_comment` / `restore_comment` | `wall` / `photos` / `video` / `market` / `board` `.deleteComment` + `.restoreComment` | **user** | board/discussion — `group_id` + `topic_id` |
| `ban_user` / `unban_user` | `groups.ban` / `groups.unban` | **user** | права администратора группы |
| `reply_comment` | `wall.createComment` (`reply_to_comment`) | **user** (fallback community) | |
| `notify_author` | `messages.isMessagesFromGroupAllowed` + `messages.send` | **community** | |
| reconciliation `publish_suggested` | `wall.get` (`filter=suggests`) + `wall.getById` | **user** | до ретрая при `UNKNOWN_OUTCOME` |

**Лимиты API:** user **3** rps, community **20** rps, `execute` ≤ **25** вызовов в одном коде ([api-requests](https://dev.vk.ru/ru/api/api-requests)).

### Telegram (Bot API)

| Действие | Метод | Поддержка |
|---|---|---|
| `delete_post` / `delete_comment` | `deleteMessage` / `deleteMessages` | окно **&lt; 48 ч** |
| `ban_user` | `banChatMember` (`restrictChatMember` при restrict) | |
| `restore_comment` | — | **false** (не поддерживается) |
| `reply_comment` | `sendMessage` (`reply_to_message_id`) | |
| `notify_author` | `sendMessage` (DM / chat) | |

**Лимиты:** ~**1** msg/s на чат, **20**/мин на группу, ~**30**/s глобально ([FAQ](https://core.telegram.org/bots/faq)).

### Max

| Действие | Метод | Поддержка |
|---|---|---|
| `delete_post` | `DELETE /messages` | |
| `delete_comment` | `DELETE …/comments` | **без restore** |
| `reply_comment` | `POST …/comments` | |
| `ban_user` | `DELETE /chats/{chatId}/members` | |
| `restore_comment` / `unban_user` / `notify_author` / `publish_suggested` | — | не поддерживается |

**Лимит:** **30** rps глобально ([prepare](https://dev.max.ru/docs/chatbots/bots-coding/prepare)).

---

## 9. Inbound events (BE-32, проверка 2026-11-18)

| Сеть | Приём | Секрет | Ретраи | Примечание |
|---|---|---|---|---|
| VK Callback API | `POST /hooks/vk/{channelId}` | JSON `secret`; confirmation code из `groups.getCallbackConfirmationCode` | 10с / 3м / 10м / 30м / 1ч | Один наш Callback-сервер на сообщество; ответ `ok` text/plain; `event_id` для дедупа |
| Telegram Bot API | `POST /hooks/tg/{botId}` | `X-Telegram-Bot-Api-Secret-Token` | хранение update ≤ 24 ч | webhook ⊕ getUpdates; альбомы `media_group_id` (окно 3 с); кастомный бот → takeover warning |
| Max | `POST /hooks/max/{channelId}` | `X-Max-Bot-Api-Secret` (5–256 `[A-Za-z0-9_-]`) | до 10×, ×2.5; автоотписка 8 ч | только HTTPS:443; health-job переподписывает |

Источники: [dev.vk.com](https://dev.vk.com), Telegram Bot API 10.4, [dev.max.ru](https://dev.max.ru) / platform-api2.max.ru.

---

## 10. Открытые пункты (unknown → бэклог)

| Сеть | Что уточнить | Зачем |
|---|---|---|
| VK | Числовой max длины `message` / комментария | Счётчик в редакторе |
| VK | Лимит размера видео на стену | posts-media |
| OK | Число символов текста (601) и max media blocks | валидация |
| OK | Pin / first comment / webhooks | паритет фич |
| Max | First comment / stories / native schedule | roadmap |
| TG / Max / Rutube | Числовой TTL bot/partner refresh где не указан | token-refresh scheduler |
| Все | Суточные лимиты публикаций | планирование пиков |

---

## История изменений

| Версия | Дата | Изменение |
|---|---|---|
| 1.2.0 | 2026-11-18 | BE-32: inbound Callback/webhook ограничения VK/TG/Max (§9) |
| 1.1.0 | 2026-11-18 | BE-33: секция «Модерация и события» (VK/TG/Max методы, ключи, лимиты) |
| 1.0.0 | 2026-11-17 | AN-2: первая матрица VK / Telegram / OK / Max / Rutube + YAML + JSON Schema |
