# Отложенные истории (Scheduled Stories)

| Поле | Значение |
|---|---|
| Версия | 1.0.1 |
| Статус | Готово к реализации (DoR); patch: Q-STORY-05 Instagram вне scope |
| Дата | **2026-11-17** |
| Эпик | **E11** - Истории VK/OK, пакетная загрузка |
| Задачи | BE-16, FE-13, QA-11; маркировка - BE-15/16 (M7) |
| Приоритет паритета | **P0** (матрица: S1–S8) |
| Зависимости | E3 (каналы VK/OK), E4 (календарь/редактор), E5 (конвейер), E10 (ОРД / ads) |
| Связанные документы | [`../parity-matrix.md`](../parity-matrix.md) §4 · [`../network-capabilities.md`](../network-capabilities.md) · [`../../analysis.md`](../../analysis.md) §2.2 · [`../../adr/0004-publishing-pipeline.md`](../../adr/0004-publishing-pipeline.md) · [`../../qa/test-cases/E11-stories.md`](../../qa/test-cases/E11-stories.md) |
| Паритет с | [vposter «Отложенные истории»](https://help.vposter.ru/informaciya-neobkhodimaya-dlya-ispolzovaniya-po/avto-ustanovki/otlozhennyi-posting/otlozhennye-istorii) · [API v3 `stories.*`](https://help.vposter.ru/razrabotchikam/api) |

---

## 1. Цели

1. **Паритет ключевого инструмента №2 Впостера:** планирование, повтор, пауза/resume, удаление, пакетная загрузка и (для VK/OK) маркировка историй.
2. **Надёжность как у постов:** тот же публикационный конвейер (`publications` + `scheduled_actions`), SLA p99 ≤ 60 с от `run_at`, 0 потерь / 0 дублей в сети.
3. **Честная матрица сетей:** в продукте - **VK** и **OK**; Telegram/Max/Rutube - out of scope (API); **Instagram* окончательно вне scope** (решение заказчика Q-STORY-05).
4. **Переиспользование модели постов:** один пайплайн, `content_kind = post | story` (рекомендация §6), без отдельного «островного» сервиса историй.

**Не цели этой спецификации:** Stories-бот VK (Фаза 3), конструктор холста/стикеров уровня SMMplanner, публикации историй в личный профиль пользователя (только сообщества/страницы каналов Posts).

---

## 2. Сценарии (пользовательские)

| ID | Актёр | Сценарий | Результат |
|---|---|---|---|
| SC-ST-01 | Editor+ | Создать одиночную историю (фото) на VK-сообщество на дату в будущем | Запись `content_kind=story`, статус `scheduled`, в календаре с меткой «История» |
| SC-ST-02 | Editor+ | Создать видео-историю на OK-группу | Публикация через Moments API в назначенное время |
| SC-ST-03 | Editor+ | Задать TTL показа 1 / 12 / 24 / 48 ч | В UI сохранён `stories_time`; на OK фактически применяется ≤24 ч (см. §5.2) |
| SC-ST-04 | Editor+ | Добавить кнопку-ссылку (текст из enum + URL) для VK | После публикации в сообществе отображается CTA; URL валидируется под правила VK |
| SC-ST-05 | Editor+ | Повторяющаяся история (`everyday` / будни / …) до `stop_at` | Материализуются экземпляры; пауза останавливает будущие |
| SC-ST-06 | Admin/Owner | Подключить VK-сообщество **отдельно для историй** (scope `stories`) | Канал получает флаг `story_access=ready`; без него UI блокирует выбор |
| SC-ST-07 | Editor+ | Пакетно загрузить N медиа → N историй | Создано N сущностей с общей датой/шагом или индивидуальным слотом |
| SC-ST-08 | Editor+ | Включить «Маркировать рекламу» для истории VK/OK | Перед публикацией создаётся креатив ОРД, в историю уходит erid / пометка |
| SC-ST-09 | Editor+ | Автоудаление из сети через N ч / на дату | `scheduled_actions` типа `delete_story` |
| SC-ST-10 | Author | Создать черновик истории | Черновик сохраняется; schedule → 403 |
| SC-ST-11 | Editor+ | Попытка выбрать Telegram / Max / IG* как сеть истории | Сети скрыты или disabled + пояснение out of scope |
| SC-ST-12 | Система | Ошибка API сети / отсутствие scope | `failed_retryable` или `failed_final` + уведомление; история не «сгорает» молча |

---

## 3. UI: экран «Создать историю» - полная спецификация полей

Навигация: **Контент → Истории → Создать** (отдельный поток от «Создать пост»; в календаре - фильтр «Истории»).  
Состав экрана (паритет help Впостера + расширения Posts ★):

### 3.1. Медиа

| Поле UI | API / модель | Обязательность | Правила |
|---|---|---|---|
| Медиа истории | `media_asset_id` **или** `media_url` (паритет vposter `stories_image`) | **Да** (ровно 1 медиа на историю) | Фото или видео; валидация по capabilities выбранных сетей (§5). Предпросмотр 9:16. |
| Тип медиа | `media_kind`: `photo` \| `video` | Выводится из файла | Смена файла пересчитывает валидацию |
| Обрезка / кадр ★ | `crop` (опционально) | Нет | Для фото - safe-area 9:16; не обязателен для MVP, если файл уже вертикальный |

**Пакетная загрузка (отдельный режим «Пакет историй»):** multi-file picker → таблица строк (файл, канал(ы), дата/время, TTL). Каждая строка → отдельная сущность `content_kind=story`. Лимит пакета: **50** файлов за операцию (продуктовое допущение; конфиг `posts.stories.batch_max=50`).

### 3.2. Каналы / группы

| Поле UI | API / модель | Обязательность | Правила |
|---|---|---|---|
| Каналы | `channel_ids[]` | **Да** (≥1) | Только каналы с `capabilities.stories=true` и статусом `ready` для stories |
| Набор страниц | `channel_set_id` | Нет | Разворачивается в список каналов (как у постов) |
| Предупреждение VK stories | - | - | Если у VK-канала нет отдельного story-подключения - карточка канала disabled + CTA «Подключить истории» |

### 3.3. Расписание и повторы

| Поле UI | API / модель | Обязательность | Соответствие vposter |
|---|---|---|---|
| Внутреннее название | `title` / `name_autopost` | Нет | `name_autopost` |
| Дата и время публикации | `scheduled_at` (TZ пользователя → UTC) | Да при schedule; «сейчас» = now | `date_post` |
| Периодичность | `recurrence.frequency` | Да (по умолчанию `once`) | `post_day` (тот же enum, см. §3.6) |
| Остановить повторы | `recurrence.until` / `stop_at` | Нет | `stop_post_date` |
| Удалить из сервиса после публикации | `delete_after_publish` | Нет (default false) | `del_task` 0/1 |

### 3.4. Параметры показа и ссылки

| Поле UI | API / модель | Обязательность | Соответствие vposter |
|---|---|---|---|
| Время показа (TTL) | `stories_time` enum | Да (default `24h`) | `0=24h, 1=1h, 2=12h, 3=48h` |
| Текст кнопки-ссылки | `link_text` enum | Нет; если задан URL - оба обязательны | `stories_link_text` |
| URL ссылки | `link_url` | Нет; парно с `link_text` | `stories_link_url` |

**Enum `stories_time` (Posts):**

| Код Posts | Значение vposter | Длительность |
|---|---|---|
| `ttl_1h` | `1` | 1 час |
| `ttl_12h` | `2` | 12 часов |
| `ttl_24h` | `0` | 24 часа (default) |
| `ttl_48h` | `3` | 48 часов |

**Enum `link_text` (полный список vposter API + VK):** хранить машиночитаемо в `NetworkCapabilities.vk.story_link_texts[]`. UI показывает русские подписи.

| Ключ | Подпись RU |
|---|---|
| `to_store` | В магазин |
| `more` | Ещё |
| `book` | Забронировать |
| `order` | Заказать |
| `enroll` | Записаться |
| `fill` | Заполнить |
| `signup` | Зарегистрироваться |
| `buy` | Купить |
| `ticket` | Купить билет |
| `write` | Написать |
| `open` | Открыть |
| `learn_more` | Подробнее |
| `view` | Посмотреть |
| `go_to` | Перейти |
| `contact` | Связаться |
| `watch` | Смотреть |
| `play` | Слушать |
| `install` | Установить |
| `read` | Читать |
| `vote` | Голосовать |
| `congratulate` | Поздравить |
| `game` | Играть |
| `to_memories_app` | К воспоминаниям |
| `calendar` | Открыть календарь |
| `watch_clip` | Смотреть клип |
| `make_clip` | Снять клип |
| `participate` | Участвовать |
| `open_mask` | Открыть маску |
| `try_on` | Примерить |
| `try` | Попробовать |
| `profile` | Открыть профиль |
| `attach_wall` | Открыть запись |
| `attach_article` | Читать статью |
| `attach_poll` | Открыть опрос |
| `attach_story` | Смотреть историю |
| `attach_audio` | Слушать |
| `attach_audio_playlist` | Открыть плейлист |
| `attach_audio_album` | Открыть альбом |
| `attach_video` | Смотреть видео |
| `attach_video_live` | К трансляции |
| `attach_photo` | Открыть фото |
| `attach_album` | Открыть фотоальбом |
| `attach_page` | Открыть страницу |
| `attach_market` | Открыть товар |
| `attach_link` | Открыть ссылку |
| `attach_artist` | Перейти к музыканту |
| `attach_narrative` | Смотреть момент |
| `attach_podcast` | Слушать подкаст |

> **Валидация VK:** официальный `stories.getPhotoUploadServer` допускает урезанный набор `link_text` и требует **внутренний** URL (`https://vk.com/…` / `https://vk.ru/…`, max 2048). UI: при сети только VK - жёсткая валидация; при мультиканале VK+OK - предупреждение и отдельный `link_url` per-network в варианте (★), либо общий URL с fail на VK при несоответствии.

### 3.5. Автоудаление и жизненный цикл

| Поле UI | API / модель | Соответствие vposter |
|---|---|---|
| Удалить из соцсети через N ч:м | `auto_delete_after` (duration) | `delete_clock` (0–4382 ч) + `delete_minutes` (0–59) |
| Удалить из соцсети в дату | `auto_delete_at` | `delete_post_date` (+ время по правилам продукта) |
| Пауза повторов с даты | `stop_at` | `stop_post_date` |

Взаимоисключение: нельзя одновременно задать бессмысленную комбинацию `auto_delete_after` и `auto_delete_at` с `auto_delete_at` раньше `scheduled_at` - API `422`.

### 3.6. Периодичность (`post_day` → `Recurrence`)

Полный enum Впостера (обязателен для импорта E22 и паритета API):

`oneraz`, `everyday`, `budday`, `weekends`, `every2day` … `every12day`, `everyweek`, `every2week`, `every3week`, `everymonth` … `every12month`.

Маппинг в Posts `Recurrence` (как у постов):

| vposter | Posts `frequency` |
|---|---|
| `oneraz` | `once` |
| `everyday` | `daily` |
| `budday` | `weekdays` |
| `weekends` | `weekends` |
| `everyNday` | `every_n_days` + `interval=N` |
| `everyweek` / `everyNweek` | `weekly` / `every_n_weeks` |
| `everymonth` / `everyNmonth` | `monthly` / `every_n_months` |

### 3.7. Маркировка рекламы (блок)

| Поле UI | API / модель | Правило |
|---|---|---|
| Переключатель «Маркировать рекламу» | `ads.enabled` | Доступен **только** если среди выбранных каналов есть VK и/или OK |
| Данные рекламодателя | `ads` / `ads_items` | Те же поля, что у постов (fio, inn, kpp, type, kktus, contract_*, vat_rate, foreign_*, `stories_summ`, …) - см. E10 / scheduled-posts |
| Провайдер ОРД | `ord_provider` | VK ОРД / Яндекс ОРД (из настроек workspace) |

Источники: [маркировка через Впостер](https://help.vposter.ru/informaciya-neobkhodimaya-dlya-ispolzovaniya-po/avto-ustanovki/otlozhennyi-posting/avtomaticheskaya-markirovka-reklamy-cherez-vposter) - «Истории доступны только для ВКонтакте и Одноклассников».

### 3.8. Действия экрана

- **Сохранить черновик** - Author+
- **Запланировать** / **Опубликовать сейчас** - Editor+ (`posts.schedule`)
- **Предпросмотр** ★ - телефонный фрейм 9:16, CTA, бейдж «Реклама» если ads
- **Создать пакет** - переход в batch-режим

---

## 4. Подключение каналов для историй

| Сеть | Отдельное подключение? | Детали |
|---|---|---|
| **VK** | **Да** | Как у Впостера: нужен community token / право **`stories`**. Scope `stories` для user token «выдаётся в исключительных случаях» (dev.vk.ru) - в Posts ориентируемся на **токен сообщества** с правом stories. UI: «Подключить истории» на карточке канала / в разделе Истории. Поле канала: `story_connection_status`: `missing` \| `pending` \| `ready` \| `revoked` \| `error`. |
| **OK** | **Нет** | Достаточно уже подключённого аккаунта/группы с правами Moments: `PHOTO_CONTENT` + `VALUABLE_ACCESS` ([moments.getUploadUrl](https://apiok.ru/dev/methods/rest/moments/moments.getUploadUrl)). |
| **IG*** | N/A | Out of product scope (§5.5). |
| **Telegram / Max** | N/A | Истории не в scope (§5.3–5.4). |

Источник UX: [otlozhennye-istorii](https://help.vposter.ru/informaciya-neobkhodimaya-dlya-ispolzovaniya-po/avto-ustanovki/otlozhennyi-posting/otlozhennye-istorii) - «Для ВКонтакте нужно отдельно подключать группы… Для Одноклассники и Instagram* подключать отдельно не нужно».

---

## 5. Матрица сетей, лимиты API и цитаты

Дата проверки фактов: **2026-11-17**.

### 5.1. Сводка продукта Posts

| Сеть | В продукте | Фаза | Истории | Маркировка историй | Примечание |
|---|---|---|---|---|---|
| VK | да | Фаза 2 (P0 паритет) | да | да | Отдельный story-connect |
| OK (Моменты) | да | Фаза 2 (P0) | да | да | TTL фактически ~24 ч |
| Telegram | канал - **нет** | Phase 3 research | out of scope | - | Bot API `postStory` только business |
| Max | **нет** | - | unsupported | - | Публичный API stories unknown |
| Instagram* | **нет** | - | out of scope | - | Meta - экстремистская орг. в РФ |
| Rutube | нет | - | нет | - | Нет stories в PartnerAPI |
| Facebook* | нет | - | - | - | Как IG* |

### 5.2. VK Stories

| Параметр | Значение | Источник | Проверено |
|---|---|---|---|
| Методы загрузки | `stories.getPhotoUploadServer` → upload → `stories.save`; видео: `stories.getVideoUploadServer` | [story-in-profile](https://dev.vk.ru/ru/api/upload/story-in-profile), [getPhotoUploadServer](https://dev.vk.ru/en/method/stories.getPhotoUploadServer) | 2026-11-17 |
| Фото форматы | JPG, PNG, GIF, WEBP, HEIF/HEIC | story-in-profile | 2026-11-17 |
| Фото разрешение | min 1×1; max 8192×8192; сторона >2560 → downscale до 2560 | story-in-profile | 2026-11-17 |
| Видео | H.264 + AAC, MP4; **≤ 350 МБ** | story-in-profile | 2026-11-17 |
| Токен | Community access token, право **`stories`**; user `stories` - по запросу в support VK | getPhotoUploadServer | 2026-11-17 |
| Ссылка | `link_text` + `link_url`; только для community stories; URL - внутренние vk.com/vk.ru, ≤2048 | getPhotoUploadServer | 2026-11-17 |
| Удаление | `stories.delete` (ожидаемо в адаптере `deleteStory`) | Stories API | 2026-11-17 |
| RPS | user 3/с; community 20/с | api-requests | 2026-11-17 |

**Адаптер:** `VkSocialAdapter.publishStory` / `deleteStory`.

### 5.3. OK Moments

| Параметр | Значение | Источник | Проверено |
|---|---|---|---|
| Пайплайн | `moments.getUploadUrl` → multipart upload → `moments.commit` (`owner_ref=group:…`) | [moment_upload](https://apiok.ru/dev/examples/moment_upload), [moments.commit](https://apiok.ru/dev/methods/rest/moments/moments.commit) | 2026-11-17 |
| Права | `PHOTO_CONTENT`, `VALUABLE_ACCESS` | moments.getUploadUrl | 2026-11-17 |
| TTL жизни момента | **~24 ч** (продукт OK; в примере API `ttl_ms` ≈ 86 400 000) | [ok.ru/groupmoments](https://ok.ru/page/groupmoments), example getUploadUrl | 2026-11-17 |
| Тип upload | `PHOTO` \| `VIDEO` \| `OVERLAY` | getUploadUrl | 2026-11-17 |
| Лимит видео-сегмента | **≤ 1 мин** - продуктовое допущение Posts (см. §16); UI режет/предупреждает | не зафиксировано числом в публичном описании метода | 2026-11-17 |
| Лимит публикаций/сутки | **до 100/день** - продуктовое допущение Posts (`posts.ratelimit.ok.moments_per_day=100`) | не в публичном REST overview | 2026-11-17 |
| Ссылка в моменте | Поддерживается продуктом OK (кнопка); передача через API - уточнить в интеграции BE-16 | ok.ru how-to-create-content | 2026-11-17 |

**Поведение TTL в UI:** для OK при выборе 1h/12h/48h - показать warning: «Моменты OK живут ~24 ч; будет применено 24 ч» и записать `effective_ttl=24h` в publication meta. Не блокировать сохранение (паритет с возможностью выбрать 48h во Впостере, но честный UX ★).

### 5.4. Telegram - OUT OF SCOPE / Phase 3 research

| Факт | Источник |
|---|---|
| Bot API 9.0+: `postStory` - **только** для managed **business accounts** с правом `can_manage_stories` | [postStory](https://core.telegram.org/bots/api#poststory), [BusinessBotRights](https://core.telegram.org/bots/api#businessbotrights) |
| `active_period`: 6h / 12h / 24h / 48h (сек.) | postStory |
| Публикация **историй канала** через Bot API **не** документирована | Bot API changelog / docs |
| Канальные stories: MTProto `stories.sendStory`, нужны boosts + admin `post_stories` | [Telegram Stories](https://core.telegram.org/api/stories) |

**Решение Posts MVP/Фаза 2:** Telegram stories = **вне scope**. В UI сеть Telegram не предлагается для `content_kind=story`. Phase 3: отдельное исследование (business-bot path vs user-client MTProto + compliance). Зафиксировано в parity-matrix S9.

### 5.5. Max - unsupported

Публичная документация [dev.max.ru/docs-api](https://dev.max.ru/docs-api) на 2026-11-17 **не** подтверждает stories. В `network-capabilities`: `stories: unknown`. **Решение:** трактовать как **unsupported** до подтверждения владельцем/документацией Max. UI не показывает Max для историй.

### 5.6. Instagram* / Facebook*

Meta (вкл. Instagram, Facebook) признана экстремистской организацией в РФ (дисклеймер help Впостера). У Впостера есть `ig_business`, но **заказчик (Q-STORY-05) окончательно исключил Instagram из scope Posts** - не реализуем, не планируем «после юр. решения», UI/API не содержат IG как целевую сеть историй.

### 5.7. Соответствие полям vposter `stories.create`

| vposter | Posts | Примечание |
|---|---|---|
| `id_group` | `channel_ids[]` | Один create → 1..N publications |
| `name_autopost` | `title` | |
| `stories_time` | `stories_time` / `ttl` | enum §3.4 |
| `type_social` ∈ {vk, ok, ig_business} | `network` ∈ {vk, ok} | ig исключён |
| `date_post` | `scheduled_at` | |
| `post_day` | `recurrence` | |
| `stories_image` | `media` (обязателен) | + видео |
| `stories_link_url` / `stories_link_text` | `link_url` / `link_text` | |
| `delete_*` | `auto_delete_*` | |
| `stop_post_date` | `stop_at` | |
| `del_task` | `delete_after_publish` | |
| `ads_items` | `ads` | только VK/OK |

Операции vposter: `stories.getList/getById/create/edit/delete/pause/resume` → Posts: CRUD + pause/resume на той же модели, что посты.

Лимиты Впостера (для импорта/паритета поведения, не как жёсткий лимит Posts): HTTP 409 «слот занят»; 429 «не более 25 историй на одно время на разных страницах» - у Posts свои лимиты тарифа + rate limit сетей.

---

## 6. Модель данных (рекомендация)

### 6.1. Решение: расширить `posts`, не плодить `stories`

**Рекомендация (утвердить на груминге BE-16):** хранить истории как записи в таблице **`posts`** с полем:

```text
content_kind ENUM ('post', 'story')  -- default 'post'
```

| Альтернатива | Плюсы | Минусы | Вердикт |
|---|---|---|---|
| A. `posts.content_kind` + story-поля в `post_variants.options` / `story_options` JSONB | Один календарь, bulk, RBAC, конвейер, импорт | Чуть шире схема поста | **Выбрать** |
| B. Отдельная таблица `stories` | Чистая семантика | Дубль scheduler, calendar, RBAC, ads, audit | Отклонить для Фазы 2 |
| C. `posts` + 1:1 `story_details` | Нормализация | Лишний join без выгоды | Допустимо как рефакторинг позже |

### 6.2. Поля сущности

Поверх обычного поста:

| Поле | Тип | Описание |
|---|---|---|
| `content_kind` | enum | `post` \| `story` |
| `story_ttl` | enum | `ttl_1h`…`ttl_48h` |
| `story_link_text` | varchar nullable | |
| `story_link_url` | text nullable | |
| `story_options` | jsonb | per-network overrides (effective_ttl OK, link per network) |

`publications`:

| Поле | Описание |
|---|---|
| `content_type` / через join post | `story` |
| `remote_story_id` | ID в сети (VK story id / OK moment id) |
| `idempotency_key` | `sha256(variant_id \|\| run_at \|\| 'story')` |

`scheduled_actions` (как у постов, ADR-0004):

| `action_type` | Когда |
|---|---|
| `delete_story` | автоудаление из сети |
| `ord_report` / удаление рекламной истории | по правилам E10 |

`channels`:

| Поле | Описание |
|---|---|
| `story_connection_status` | см. §4 |
| `story_token_ref` | ссылка на encrypted token (если отделён от wall token) |

### 6.3. Индексы / календарь

- Календарь и лента фильтруют `content_kind`.
- Unique (мягкий): предупреждение при >N историй одного канала на тот же `scheduled_at` (конфиг), без жёсткого 409 Впостера - ★ прозрачнее для агентств.

---

## 7. Архитектура публикации

```text
API schedule(story)
  → posts (content_kind=story) + publications(pending) + outbox
       ↓
scheduler (SKIP LOCKED) → queued → RabbitMQ publish.<network>
       ↓
publisher-<network>:
  claim → SocialAdapter.publishStory(...) → succeeded | failed_*
       ↓
scheduled_actions → actions.<network> → deleteStory / …
```

Расширения контракта адаптера:

```java
public interface SocialAdapter {
  // существующие publishPost / deletePost / …
  PublishResult publishStory(StoryPublishCommand cmd);
  void deleteStory(StoryDeleteCommand cmd);
  Optional<RemoteStoryRef> findExistingStory(String idempotencyKey);
}
```

- Очереди те же `publish.vk` / `publish.ok` (поле `content_type=story` в payload) **или** отдельные `publish.vk.story` - решение BE: предпочтительно **тот же** consumer с ветвлением по `content_type`, чтобы не плодить Deployment’ы.
- Медиа: предварительно в S3/media-service; адаптер качает/стримит на upload URL сети.
- Идемпотентность: до вызова `save`/`commit` - проверка `remote_story_id`; для VK по возможности `findExisting` по метаданным.

---

## 8. Предложения OpenAPI (`/api/v1`)

Базовый префикс совместим с текущим `openapi.yaml` (ресурсы posts). Не вводить параллельный `/stories`, если выбран путь A (§6) - фильтр и отдельные operationId.

### 8.1. Схемы (фрагменты)

```yaml
ContentKind:
  type: string
  enum: [post, story]

StoryTtl:
  type: string
  enum: [ttl_1h, ttl_12h, ttl_24h, ttl_48h]

StoryLinkText:
  type: string
  description: VK/OK CTA key; полный enum из §3.4
  example: go_to

StoryOptions:
  type: object
  properties:
    ttl:
      $ref: '#/components/schemas/StoryTtl'
    linkText:
      $ref: '#/components/schemas/StoryLinkText'
    linkUrl:
      type: string
      format: uri
    autoDeleteAfterMinutes:
      type: integer
      minimum: 0
      maximum: 262920   # 4382h * 60
    autoDeleteAt:
      type: string
      format: date-time
    deleteAfterPublish:
      type: boolean
    ads:
      $ref: '#/components/schemas/AdsMarking'

Post:
  properties:
    contentKind:
      $ref: '#/components/schemas/ContentKind'
    story:
      $ref: '#/components/schemas/StoryOptions'
```

### 8.2. Эндпоинты

| Method | Path | Описание |
|---|---|---|
| `POST` | `/api/v1/posts` | Создать черновик/schedule; body включает `contentKind: story` + `story` |
| `GET` | `/api/v1/posts?contentKind=story` | Список историй |
| `GET` | `/api/v1/posts/{postId}` | Карточка |
| `PATCH` | `/api/v1/posts/{postId}` | Редактирование до публикации |
| `POST` | `/api/v1/posts/{postId}/schedule` | Запланировать |
| `POST` | `/api/v1/posts/{postId}/pause` \| `/resume` | Пауза/resume повторов |
| `DELETE` | `/api/v1/posts/{postId}` | Удалить задачу (+ опционально remote) |
| `POST` | `/api/v1/stories/batch` | Пакетное создание (multipart или JSON mediaIds[]) |
| `POST` | `/api/v1/channels/{channelId}/story-connection` | Старт OAuth/подключения VK stories |
| `GET` | `/api/v1/channels/{channelId}/story-connection` | Статус |
| `GET` | `/api/v1/networks/capabilities` | Уже есть; убедиться `features.stories` + `story_link_texts` + media limits |

Ошибки Problem+JSON: `story_connection_required`, `story_unsupported_network`, `story_media_invalid`, `story_link_invalid`, `story_ttl_unsupported`, `ads_not_allowed_for_network`, `ord_unavailable`.

---

## 9. Состояния и ошибки

### 9.1. Статусы сущности (как у постов)

`draft` → `scheduled` → `publishing` → `published` \| `partially_failed` \| `failed`  
Дополнительно: `paused` (повторы), `deleted`.

Статусы publication: `pending` → `queued` → `in_progress` → `succeeded` \| `failed_retryable` \| `failed_final` \| `waiting_for_channel` (нет/отозван story token).

### 9.2. Типовые ошибки

| Код / условие | HTTP | Поведение UI |
|---|---|---|
| Нет медиа | 422 | Блок submit |
| VK без story connect | 422 `story_connection_required` | CTA подключить |
| Сеть TG/Max/IG | 422 `story_unsupported_network` | Не показывать в picker |
| Невалидный link_url для VK | 422 `story_link_invalid` | Подсказка «только ссылки vk.com» |
| Медиа > 350 МБ VK video | 422 | |
| OK > day quota | 429 / delayed | Перенос + уведомление |
| Scope stories отозван mid-flight | `waiting_for_channel` | Алерт Admin |
| ОРД недоступен при ads=true | 503 / fail schedule | Не публиковать «тихую» рекламу без erid |
| Конфликт слота (опционально) | 409 | Как политика тарифа |

---

## 10. Надёжность

| Требование | Как |
|---|---|
| SLA публикации | Тот же p99 ≤ 60 с (`posts_publication_delay_seconds{content_type=story}`) |
| 0 потерь | Состояние в PG; reaper; retry ≤ 8 / 60 мин |
| 0 дублей | idempotency_key + findExistingStory |
| Автоудаление | `scheduled_actions` + идемпотентный `deleteStory` |
| Наблюдаемость | `trace_id`; метки `content_kind=story`; DLQ alert |
| Деградация OK TTL | effective_ttl в meta, без падения job |

---

## 11. RBAC

Права - те же, что у постов (`roles-permissions.md` §3.4), с уточнением:

| Действие | Право | Owner | Admin | Editor | Author | Viewer |
|---|---|:---:|:---:|:---:|:---:|:---:|
| Черновик истории | `posts.create` | ✔ | ✔ | ✔ | ✔ | – |
| Планировать / опубликовать | `posts.schedule` | ✔ | ✔ | ✔ | – | – |
| Bulk / pause / resume | `posts.bulk` | ✔ | ✔ | ✔ | – | – |
| Подключить VK stories | `channels.manage` | ✔ | ✔ | – | – | – |
| Смотреть в календаре | `calendar.view` | ✔ | ✔ | ✔ | ✔ | ✔ |
| Маркировка (заполнение ads) | `posts.schedule` (+ доступ к ОРД workspace) | ✔ | ✔ | ✔ | – | – |

IDOR: чужой workspace → **404**. Author: только `own_posts` до schedule.

---

## 12. Аудит

Писать в audit-log (как US-2.4):

| Событие | payload (без секретов) |
|---|---|
| `story.created` / `updated` / `deleted` | postId, channelIds, scheduledAt |
| `story.scheduled` / `published` / `failed` | publicationId, network, errorCode |
| `story.batch_created` | count, channelIds |
| `story.connection.started` / `ready` / `revoked` | channelId, network=vk |
| `story.ads.enabled` | ordProvider, advertiserInn (masked) |

---

## 13. Юридическое: маркировка рекламы для историй

1. Рекламные истории подпадают под **ФЗ-38 ст. 18.1** так же, как посты.
2. Паритет Впостера: маркировка историй в продукте - **VK и OK**; help прямо: «Истории доступны только для ВКонтакте и Одноклассников» в разделе маркировки истории.
3. Поток: ads-флаг → создание/reuse контрагента и креатива в ОРД (E10) → получение **erid** → передача в адаптер (пометка «Реклама» / нативные поля сети) → учёт в отчётности; автоудаление рекламных историй - по политике E10 (M5).
4. Без успешного erid **не** публиковать рекламную историю (fail-closed) - ★ жёстче, чем «опубликовать и потом чинить».
5. Самореклама / foreign / НДС - поля `ads_items` как у постов (M10).

Ссылки: [ФЗ-38](https://www.consultant.ru/document/cons_doc_LAW_58968/) · [ОРД VK](https://ord.vk.ru/help/api/api) · [help Впостер маркировка](https://help.vposter.ru/informaciya-neobkhodimaya-dlya-ispolzovaniya-po/avto-ustanovki/otlozhennyi-posting/avtomaticheskaya-markirovka-reklamy-cherez-vposter).

---

## 14. Критерии приёмки (AC) и трассировка на TC

### AC-STORY-01 - Создание фото-истории VK
**Given** VK-канал со `story_connection_status=ready`, валидное фото  
**When** Editor планирует историю на T+1h, ttl=24h  
**Then** `content_kind=story`, publication `scheduled`; в T эмулятор/сеть получает story; статус `published`

### AC-STORY-02 - Видео OK Moments
**Given** OK-группа подключена  
**When** schedule видео-истории  
**Then** вызван пайплайн moments.*; remote id сохранён

### AC-STORY-03 - TTL enum
**Given** выбор ttl 1/12/24/48h  
**When** save  
**Then** значение сохранено; для OK effective_ttl=24h при любом выборе с warning

### AC-STORY-04 - Ссылка VK
**Given** `link_text=go_to`, `link_url=https://vk.com/club1`  
**When** publish VK  
**Then** CTA передана в getPhotoUploadServer; внешний URL → 422

### AC-STORY-05 - Отдельный VK connect
**Given** канал VK без stories scope  
**When** попытка schedule  
**Then** 422 `story_connection_required`; после успешного connect - ok

### AC-STORY-06 - OK без отдельного connect
**Given** OK канал `ready` для постов с нужными permissions  
**When** schedule story  
**Then** отдельный story-connect не требуется

### AC-STORY-07 - Повторы и pause/resume
**Given** recurrence everyday until D  
**When** pause  
**Then** будущие publications не стартуют; resume продолжает

### AC-STORY-08 - Автоудаление
**Given** auto_delete_after=2h  
**When** story published  
**Then** создан `scheduled_actions.delete_story`; после срока - deleteStory идемпотентно

### AC-STORY-09 - Пакетная загрузка
**Given** 10 файлов, один канал, шаг +5 мин  
**When** batch create  
**Then** 10 story posts; все в календаре

### AC-STORY-10 - Маркировка VK/OK
**Given** ads.enabled + валидный ОРД  
**When** publish  
**Then** erid получен до publish; в отчёте ОРД есть запись; TG/Max недоступны для ads на story

### AC-STORY-11 - Out of scope сети
**Given** capabilities  
**When** UI/API create story для telegram|max|ig  
**Then** отказ; документированное сообщение

### AC-STORY-12 - RBAC Author
**Given** Author  
**When** schedule story  
**Then** 403; draft разрешён

### AC-STORY-13 - Надёжность / идемпотентность
**Given** повторная доставка сообщения publish  
**When** consumer отрабатывает дважды  
**Then** в сети одна история (`remote_story_id` / findExisting)

### AC-STORY-14 - Календарь и фильтр
**Given** смесь post/story  
**When** фильтр «Истории»  
**Then** видны только `content_kind=story` с отличимым маркером

Трассировка TC: [`../../qa/test-cases/E11-stories.md`](../../qa/test-cases/E11-stories.md) (`TC-STORY-01` …).

---

## 15. Преимущества Posts относительно Впостера

| ★ Posts | Зачем |
|---|---|
| Единый контент-календарь post+story с фильтрами и DnD | У Впостера истории - отдельный раздел, легко потерять слоты |
| Честные предупреждения TTL OK и ограничений VK link_url | Меньше «тихих» сюрпризов |
| Workspace / RBAC / аудит на историях | Агентствам и командам |
| Fail-closed маркировка (нет erid → нет publish) | Снижение юр. риска |
| Тот же SLA и прозрачные статусы publication | Ответ на боли «сгоревших» отложек |
| Явный out-of-scope Telegram/Max/IG* в продукте | Нет ложных обещаний |
| Пакет + предпросмотр 9:16 + per-network link override | UX массового контента |

---

## 16. Допущения и открытые вопросы

| # | Допущение / вопрос | Влияние | Владелец |
|---|---|---|---|
| A1 | OK: лимит **100 moments/день** и видео-сегмент **≤1 мин** - продуктовые лимиты Posts до появления цифр в apiok.ru | Конфиг rate limit / валидация UI | BE-16 + аналитик |
| A2 | Ссылки OK Moments через API поддерживают произвольный HTTPS (в отличие от VK) - подтвердить на интеграции | Валидатор link_url | BE-16 |
| A3 | `content_kind` на `posts` утверждён вместо отдельной таблицы | Миграции | BE-16 / ADR follow-up |
| A4 | ~~Instagram без юр. решения~~ → **РЕШЕНИЕ Q-STORY-05: Instagram окончательно вне scope** | Scope | Owner ✓ |
| A5 | Telegram stories - Phase 3 research (business bot vs MTProto) | Roadmap | Owner + аналитик |
| A6 | Max stories - unsupported until docs | Roadmap | аналитик |
| A7 | VK user-token scope `stories` может быть недоступен приложению - только community token | Онбординг connect | BE-16, FE-13 |
| A8 | Пакет max 50 - конфиг, не закон | Тарифы | продукт |
| A9 | Импорт из Впостера (E22) маппит `stories.*` → `content_kind=story` | Миграция клиентов | BE-22 |
| A10 | Видео VK duration/bitrate сверх «MP4 H.264/AAC ≤350MB» - уточнить при smoke на реальном сообществе | Валидация | QA-11 |

---

## 17. История изменений

| Версия | Дата | Изменение |
|---|---|---|
| 1.0.0 | 2026-11-17 | Первая полная спецификация E11 / scheduled stories (паритет vposter + официальные API) |
| 1.0.1 | 2026-11-17 | Q-STORY-05: Instagram окончательно вне scope |
