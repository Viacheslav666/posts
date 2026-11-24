# E11 - Отложенные истории (TC-STORY)

| | |
|---|---|
| Задача | QA-11 |
| Спецификация | [`docs/product/features/scheduled-stories.md`](../../product/features/scheduled-stories.md) |
| Эпик | E11 (BE-16, FE-13); маркировка M7 / E10 |
| Источники | help vposter otlozhennye-istorii; API v3 stories.*; network-capabilities; ADR-0004 |
| Шаблон | [`../templates/test-case.md`](../templates/test-case.md) |

## Трассировка AC → TC

| AC | Кратко | TC | Приоритет | Автоматизация |
|---|---|---|---|---|
| AC-STORY-01 | Фото-история VK | TC-STORY-01 | P0 | авто |
| AC-STORY-02 | Видео OK Moments | TC-STORY-02 | P0 | авто (эмулятор) / ручной smoke |
| AC-STORY-03 | TTL enum + OK effective 24h | TC-STORY-03 | P0 | авто |
| AC-STORY-04 | VK link_text/url | TC-STORY-04, TC-STORY-05 | P0 | авто |
| AC-STORY-05 | VK отдельный story-connect | TC-STORY-06 | P0 | авто |
| AC-STORY-06 | OK без отдельного connect | TC-STORY-07 | P0 | авто |
| AC-STORY-07 | Повторы pause/resume | TC-STORY-08 | P0 | авто |
| AC-STORY-08 | Автоудаление | TC-STORY-09 | P0 | авто |
| AC-STORY-09 | Пакетная загрузка | TC-STORY-10 | P1 | авто |
| AC-STORY-10 | Маркировка VK/OK | TC-STORY-11, TC-STORY-12 | P0 | авто + ручной ОРД staging |
| AC-STORY-11 | Out of scope TG/Max/IG | TC-STORY-13 | P0 | авто |
| AC-STORY-12 | RBAC Author | TC-STORY-14 | P0 | авто |
| AC-STORY-13 | Идемпотентность | TC-STORY-15 | P0 | авто |
| AC-STORY-14 | Календарь фильтр | TC-STORY-16 | P1 | авто |
| - | Медиа-лимиты VK | TC-STORY-17 | P0 | авто |
| - | del_task / stop_at | TC-STORY-18 | P1 | авто |
| - | IDOR чужой workspace | TC-STORY-19 | P0 | авто |
| - | waiting_for_channel | TC-STORY-20 | P0 | авто |
| - | Bulk delete stories | TC-STORY-21 | P1 | авто |
| - | Редактирование до publish | TC-STORY-22 | P1 | авто |
| - | Recurrence everyday materialize | TC-STORY-23 | P0 | авто |
| - | Ads fail-closed без erid | TC-STORY-24 | P0 | авто |
| - | Batch limit 50 | TC-STORY-25 | P2 | авто |
| - | Audit events | TC-STORY-26 | P1 | авто |
| - | Capabilities stories flags | TC-STORY-27 | P0 | contract |
| - | Max unsupported | TC-STORY-28 | P1 | авто |
| - | Telegram postStory documented OOS | TC-STORY-29 | P1 | ручной / doc |
| - | VK video ≤350MB reject | TC-STORY-30 | P0 | авто |

---

## TC-STORY-01 - Запланировать фото-историю VK

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-01 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Уровень | integration / e2e |
| Сети | VK (эмулятор) |

### Предусловия
- [ ] Workspace + проект; Editor
- [ ] VK-канал `ready`, `story_connection_status=ready`
- [ ] JPEG 1080×1920 в медиатеке
- [ ] Эмулятор VK stories включён

### Шаги
| № | Действие | Данные |
|---|---|---|
| 1 | POST `/api/v1/posts` | `contentKind=story`, media, channel VK, `scheduledAt=now+2m`, `story.ttl=ttl_24h` |
| 2 | Дождаться scheduler | - |
| 3 | Проверить publication + эмулятор | - |

### Ожидание
- Пост `content_kind=story`, publication `succeeded`
- В эмуляторе ровно 1 story; `remote_story_id` заполнен
- Метрика delay записана

---

## TC-STORY-02 - Видео-история OK Moments

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-02 |
| Приоритет | P0 |
| **Автоматизация** | **авто** (эмулятор moments.*) · **ручной** smoke на тестовой группе |
| Сети | OK |

### Ожидание
Последовательность getUploadUrl → upload → commit; `owner_ref=group:…`; remote moment id сохранён; статус published.

---

## TC-STORY-03 - TTL 1/12/24/48; OK effective 24h

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-03 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | VK, OK |

### Ожидание
Все 4 значения сохраняются в `story.ttl`. Для OK в `publication.meta.effective_ttl=ttl_24h` и UI warning; job не падает. Для VK ttl уходит в адаптер/meta как выбранный (если сеть не поддерживает кастом - зафиксировать фактическое поведение адаптера в тесте).

---

## TC-STORY-04 - VK CTA: валидная внутренняя ссылка

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-04 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | VK |

### Ожидание
`linkText=go_to`, `linkUrl=https://vk.com/club<id>` → publish ok; эмулятор получил оба параметра на getPhotoUploadServer.

---

## TC-STORY-05 - VK CTA: внешний URL → 422

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-04 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Тип | негативный |

### Ожидание
`linkUrl=https://example.com` при канале VK → `422 story_link_invalid`; история не создаётся в `scheduled`.

---

## TC-STORY-06 - VK без story-connect блокирует schedule

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-05 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | VK |

### Ожидание
`story_connection_status=missing` → schedule `422 story_connection_required`. После `POST .../story-connection` → `ready` - повторный schedule успешен.

---

## TC-STORY-07 - OK не требует отдельного story-connect

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-06 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | OK |

### Ожидание
Канал OK в статусе обычного `ready` без отдельного story-флага - schedule story разрешён (при наличии PHOTO_CONTENT+VALUABLE_ACCESS в токене фикстуры).

---

## TC-STORY-08 - Everyday + pause/resume

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-07 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Recurrence materialize ближайших экземпляров; `POST pause` → новые `pending` не уходят в queued; `resume` продолжает. Уже `published` не откатывается.

---

## TC-STORY-09 - Автоудаление через 2 часа

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-08 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | VK (эмулятор) |

### Ожидание
После publish создан `scheduled_actions` `delete_story` с `run_at≈published_at+2h`. По срабатыванию - `deleteStory`; повтор action идемпотентен (0 ошибок / 0 дубль-delete crash).

---

## TC-STORY-10 - Пакет из 10 медиа

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-09 |
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
`POST /api/v1/stories/batch` с 10 файлами, step 5 мин → 10 posts `content_kind=story`, 10 publications; календарь показывает все; partial fail одного файла не откатывает весь пакет (или all-or-nothing - зафиксировать выбранную политику BE; по умолчанию **best-effort с отчётом**).

---

## TC-STORY-11 - Маркировка истории VK (erid до publish)

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-10 |
| Приоритет | P0 |
| **Автоматизация** | **авто** (мок ОРД) · **ручной** staging VK ОРД |
| Сети | VK |

### Ожидание
`ads.enabled=true` + валидный рекламодатель → мок ОРД отдаёт erid → publish; в креативе/отчёте есть запись; UI предпросмотр с пометкой «Реклама».

---

## TC-STORY-12 - Маркировка истории OK; запрет для не-VK/OK

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-10 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | OK |

### Ожидание
Ads на OK story разрешены. Попытка ads на story с сетью вне VK/OK невозможна (UI hidden + API 422 `ads_not_allowed_for_network`).

---

## TC-STORY-13 - Telegram / Instagram* / Rutube недоступны для story

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-11 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Тип | негативный |

### Ожидание
Capabilities: `stories=false` / отсутствует. Create с `network=telegram|ig_business|rutube` → 422 `story_unsupported_network`. UI picker не показывает эти сети в режиме истории.

---

## TC-STORY-14 - Author: draft ok, schedule 403

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-12 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Тип | безопасность / RBAC |

### Ожидание
Author создаёт draft story; `POST schedule` → 403. Editor в том же проекте планирует успешно.

---

## TC-STORY-15 - Идемпотентность повторной доставки

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-13 |
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Уровень | integration (publisher) |

### Ожидание
Дважды доставить одно и то же сообщение `publish.*` для story → в эмуляторе **1** remote story; вторая обработка - no-op по `remote_story_id` / findExistingStory.

---

## TC-STORY-16 - Фильтр календаря «Истории»

| Поле | Значение |
|---|---|
| Story / AC | AC-STORY-14 |
| Приоритет | P1 |
| **Автоматизация** | **авто** |
| Уровень | e2e |

### Ожидание
Смесь post+story: фильтр stories показывает только story; визуальный маркер отличается от поста.

---

## TC-STORY-17 - Недопустимый формат медиа

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Тип | негативный / граничный |

### Ожидание
Файл `.bmp` / битый JPEG → 422 `story_media_invalid` на create/schedule. Для VK photo допускаются JPG/PNG/GIF/WEBP/HEIF.

---

## TC-STORY-18 - stop_at и delete_after_publish

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
`stop_at` останавливает materialize после даты. `deleteAfterPublish=true` (`del_task`) - после успешного publish сущность скрывается/удаляется из активных задач сервиса, publication history сохраняется для аудита.

---

## TC-STORY-19 - IDOR: чужой workspace

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Тип | безопасность |

### Ожидание
GET/PATCH/DELETE story другого workspace → **404** (не 403).

---

## TC-STORY-20 - Отзыв stories-токена → waiting_for_channel

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Сети | VK |

### Ожидание
Перед run_at отозвать story token в эмуляторе → publication `waiting_for_channel` + уведомление Admin/Editor; после реконнекта и retry - publish.

---

## TC-STORY-21 - Bulk delete запланированных историй

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
Выбор N scheduled stories → bulk delete; publications cancelled; уже published не удаляются из сети без явного флага `deleteRemote=true`.

---

## TC-STORY-22 - Редактирование до публикации

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
PATCH media/ttl/link до `publishing` успешен. После `published` - 409/422 (редактирование remote story - out of MVP, только delete+create).

---

## TC-STORY-23 - Материализация recurrence weekdays

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`budday`/`weekdays` до until: экземпляры только Пн–Пт в TZ пользователя; weekend слотов нет.

---

## TC-STORY-24 - Ads fail-closed без erid

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Тип | негативный |

### Ожидание
Мок ОРД возвращает 5xx/timeout при ads.enabled → publication `failed_retryable` или schedule отклонён; **в сети истории нет** (проверка эмулятора count=0 до успеха ОРД).

---

## TC-STORY-25 - Batch > 50 → 422

| Поле | Значение |
|---|---|
| Приоритет | P2 |
| **Автоматизация** | **авто** |

### Ожидание
51 файл → 422; конфиг `posts.stories.batch_max`.

---

## TC-STORY-26 - Audit: story.created / connection.ready

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
События в audit-log с postId/channelId; токены отсутствуют в payload.

---

## TC-STORY-27 - Contract: capabilities.stories

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** (contract) |
| Уровень | contract |

### Ожидание
`GET /api/v1/networks/capabilities`: VK/OK `features.stories=true`; Telegram `false`; Max `unknown`|false для продукта; список `story_link_texts` для VK не пуст; согласовано с YAML fixture.

---

## TC-STORY-28 - Max канал нельзя выбрать для story

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | **авто** |

### Ожидание
Даже при подключённом Max: picker историй без Max; API 422.

---

## TC-STORY-29 - Документация OOS Telegram (регрессия продукта)

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | **ручной** / checklist релиза |
| Тип | регрессия / doc |

### Ожидание
В UI/help Posts явно: Telegram stories out of scope (Bot API только business; каналы - MTProto). Нет кнопки «История» на TG-канале.

---

## TC-STORY-30 - VK video > 350 МБ отклоняется

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Тип | граничный |

### Ожидание
Файл 351 МБ MP4 → 422 до постановки в очередь; publication не создаётся.

---

## Чеклист выхода E11

- [ ] 100 % P0 TC-STORY пройдены на staging/эмуляторе
- [ ] 0 Blocker/Critical по историям
- [ ] OpenAPI обновлён (`contentKind`, story options, batch, story-connection)
- [ ] `network-capabilities.yaml` содержит story limits / link texts
- [ ] Ручной smoke: 1 VK community story + 1 OK group moment
- [ ] Маркировка: мок + (желательно) 1 реальный erid на staging

## История изменений

| Дата | Изменение |
|---|---|
| 2026-11-17 | Первичный набор TC-STORY-01…30 для E11 |
