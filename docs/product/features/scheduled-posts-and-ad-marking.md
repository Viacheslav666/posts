# Отложенные посты и маркировка рекламы (паритет vposter)

| Поле | Значение |
|---|---|
| Версия | 1.0.1 |
| Задача | **AN-P** |
| Дата | 2026-11-17 |
| Статус | Готово к реализации (DoR); patch: решения Q-ORD-03/06 |
| Эпики | E4 (посты), **E10** (ОРД), дополнения к US-4.* |
| Связанные | `parity-matrix.md` §2–3 · `network-capabilities.md` · `PRD-MVP.md` · ADR-0004 publishing pipeline · `roles-permissions.yaml` |
| Тест-кейсы | `docs/qa/test-cases/E10-ad-marking.md`, расширения `E4.md` (TC-POST-AD-*) |

## 1. Цель

Повторить вкладку **«Создать пост» / «Отложенные посты»** vposter.ru **целиком** (поля, опции, режимы, ограничения по сетям) и обеспечить маркировку рекламы **не хуже** оригинала: получение **erid**, пометка «Реклама», данные рекламодателя, передача креативов/статистики/актов в ОРД, автоудаление.

**Гарантии Posts:** публикация через существующий конвейер (scheduler → RabbitMQ → `publisher-<network>`), **0 потерь / 0 дублей**, p99 ≤ 60 с.

### Источники vposter

| Что | URL | Проверено |
|---|---|---|
| Постинг + маркировка (лендинг) | https://vposter.ru/page/posting | 2026-11-17 |
| Help: маркировка | https://help.vposter.ru/.../avtomaticheskaya-markirovka-reklamy-cherez-vposter | 2026-11-17 |
| API v3 `posts.create` | https://help.vposter.ru/razrabotchikam/api | 2026-11-17 |
| Функциональные характеристики | https://help.vposter.ru/funkcionalnye-kharakteristiki | 2026-11-17 |
| Анализ | `docs/analysis.md` §2.2–2.3 | 2026-11-17 |

### Источники закона и ОРД

| Что | URL |
|---|---|
| ФЗ-38 «О рекламе», ст. 18.1 | consultant.ru (закон о рекламе) |
| ОРД VK API | https://ord.vk.ru/help/api/api |
| Креативы v3 + erid | https://ord.vk.ru/help/api/ref/creative |
| ОРД Яндекс | https://ord.yandex.ru/ · https://ord-prestable.yandex.net/api/docs |
| Этапы маркировки | https://yandex.ru/support/ord/ru/marking-stages |
| Сети: VK `wall.post`, TG Bot API, OK `mediatopic.post`, Max messages | см. `network-capabilities.md` |

---

## 2. Сценарии

### S1. Создать и запланировать пост (Happy path)
Given Editor в проекте с активными каналами VK+TG  
When заполняет текст, медиа, выбирает каналы/набор, дату, опции и нажимает «Запланировать»  
Then создаётся `Post` + `PostVariant[]` + `Publication[]`; статус `scheduled`; в UI список/календарь отражают пост.

### S2. Опубликовать сейчас
When «Опубликовать сейчас»  
Then `scheduled_at ≈ now()`; публикации уходят в конвейер; статусы по WS ≤ 3 с.

### S3. Репост
When включён режим репоста и указан URL объекта (VK/TG/OK)  
Then публикуется репост с опциональным комментарием; Max/Rutube — опция скрыта.

### S4. Маркировка рекламы
Given в workspace подключен ОРД (VK или Яндекс)  
When включён флаг «Маркировать рекламу» и заполнены данные рекламодателя  
Then до публикации: контрагент/договор/креатив в ОРД → **erid**; в текст/меню поста вставляются «Реклама», рекламодатель, `erid: …`; после периода — акты/статистика; по умолчанию — автоудаление из сети.

### S5. Пауза / возобновление / bulk
Соответствует US-4.7; серии повторов — «только это / все последующие».

### S6. Ошибка ОРД до публикации
When ОРД недоступен или отказ валидации  
Then пост **не** публикуется как рекламный; статус `failed` / блокировка schedule с кодом `ord_*`; пользователь уведомлён. Fail-closed.

---

## 3. UI: полный перечень полей вкладки «Создать пост»

Экран: полноэкранный редактор (`03-frontend` A4) + панель опций.  
Defaults и валидация — ниже. Серая подсветка опции = сеть не поддерживает (из `GET /networks/capabilities`).

### 3.1. Основные

| ID поля | UI | Тип | Default | Валидация / поведение | Сети |
|---|---|---|---|---|---|
| `title` | Название в списке | string | авто из текста (≤80) | max 200 | все |
| `channelIds` / `channelSetId` | Каналы / набор | multi | — | ≥1 канал; лимит тарифа | все |
| `baseText` | Общий текст (Tiptap) | rich | "" | лимит max по выбранным сетям; HTML→TG/Max | все |
| `variants[].text` | Вариант сети | rich | null (=base) | бейдж «переопределено» | per network |
| `mediaIds` | Медиа | list | [] | форматы/кол-во по capabilities; ≤10 VK и т.д. | см. матрицу |
| `scheduledAt` | Дата/время | datetime TZ | now+1min (как vposter) | future или now; UTC в БД | все |
| `recurrence` | Повтор | enum+until | `once` | см. §3.2 | все |
| `publishMode` | Сейчас / по расписанию | enum | schedule | — | все |

### 3.2. Повтор (`post_day` ↔ Posts `recurrence`)

Паритет enum vposter API:

| vposter `post_day` | Posts `recurrence` |
|---|---|
| `oneraz` | `{freq: once}` |
| `everyday` | `{freq: daily, interval: 1}` |
| `budday` | `{freq: weekly, byweekday: [MO..FR]}` |
| `weekends` | `{freq: weekly, byweekday: [SA,SU]}` |
| `everyNday` (2–12) | `{freq: daily, interval: N}` |
| `everyweek` / `every2week` / `every3week` | `{freq: weekly, interval: 1\|2\|3}` |
| `everymonth` … `every12month` | `{freq: monthly, interval: N}` |

Дополнительно Posts ★: `until` (дата окончания серии), UI «каждый месяц 31-го» → ближайший последний день месяца (AC US-4.4).

Материализация publications на **14 дней** вперёд (как конвейер MVP).

### 3.3. Опции поста (паритет API v3)

| ID | UI | Default | Валидация | Сети |
|---|---|---|---|---|
| `signed` | Подпись автора | false | — | VK |
| `muteNotifications` | Без уведомления | false | — | VK, OK, TG, Max |
| `closeComments` | Закрыть комментарии | false | — | VK, OK |
| `pin` | Закрепить | false | — | VK, TG, Max |
| `unpinAt` | Дата открепления | null | ≥ scheduledAt если pin | VK, TG, Max |
| `autoDeleteAfter` | Автоудаление из сети | null | часы 0–4382 + минуты 0–59 **или** абсолютная дата | все с delete API |
| `stopAt` | Приостановить серию на сервисе | null | дата | все |
| `removeFromServiceAfterPublish` (`del_task`) | Удалить из Posts после публикации | false | только журнал публикаций остаётся | все |
| `coOwnerIds` | Соавторы VK | [] | ≤4; ID сообществ с `-` | VK |
| `donutDays` | VK Donut видимость | 0 (без ограничений) | 0–7 | VK |
| `primaryAttachmentsMode` | Сетка / карусель | grid | — | VK |
| `firstComment` | Первый комментарий | null | лимит комментария сети | VK (+ TG discussion ★) |
| `repost.url` | Ссылка объекта репоста | null | URL сети | VK, TG, OK |
| `repost.comment` | Комментарий к репосту | "" | — | VK, TG, OK |
| `buttons` | Inline-кнопки | [] | ≤10 пар name/url | TG, Max |
| `actionButton` | Кнопка действия VK | null | enum type_button | VK |
| `poll` | Опрос | null | question + ≥2 answers | VK, OK, TG |
| `geo` | Место | null | lat/long | VK, OK |
| `copyrightUrl` | Источник | null | HTTPS URL | VK |
| `linkSnippet` | Сниппет ссылки | null | url + title… | VK, OK, TG |
| `isAd` | Маркировать рекламу | false | если true — блок ORD обязателен | VK, OK, TG, Max, Rutube |
| `ad` | Блок рекламодателя | — | см. §3.4 | при isAd |

**Вложения:** единый `attachments` через `mediaIds` + typed blocks (poll/geo/…) в `content.jsonb`. Старый формат VK `photo-123_456` поддерживается при импорте из vposter (E22), в UI не показывается.

### 3.4. Блок маркировки рекламы (`ads_items`)

| ID | UI | Default | Валидация |
|---|---|---|---|
| `ad.advertiserName` (`fio`) | ФИО / наименование | — | required if isAd |
| `ad.inn` | ИНН | — | 10 или 12 цифр; автоподстановка имени по ИНН ★ |
| `ad.kpp` | КПП | "" | required для юрлица |
| `ad.orgType` (`type`) | Физлицо / ИП / ЮЛ / иностранное | — | enum |
| `ad.amount` (`posts_summ`) | Сумма размещения | — | ≥0, decimal |
| `ad.contractAmount` | Сумма договора | = amount | ≥0 |
| `ad.contractDate` | Дата договора | — | required unless self-promo |
| `ad.contractDateEnd` | Окончание договора | null | ≥ start |
| `ad.contractSerial` | Номер договора | "" | — |
| `ad.kktuCodes` | ККТУ | [] | ≥1 из справочника |
| `ad.oksmCountry` | Страна (ОКСМ) | RU | справочник |
| `ad.vatRate` | НДС | `without_vat` | enum |
| `ad.roles` (`person`) | Роли в цепочке | `["advertiser"]` | subset ORD roles |
| `ad.isForeign` | Иностранный рекламодатель | false | foreign_* поля |
| `ad.foreignInn` / `foreignRegNumber` / `foreignEpayment` | Реквизиты иностранца | "" | if foreign |
| `ad.phone` / `ad.address` | Контакты | "" | — |
| `ad.description` | Описание для ОРД | "" | max 1000 |
| `ad.markingType` (`type_marking`) | Режим маркировки | `0` | по справочнику ORD |
| `ad.selfPromo` | Самореклама | false | упрощённый договор (как у vposter) |
| `ad.displayPeriodDays` | Период показа для отчётности | **30** | 1–365; как vposter |
| `ad.autoDeleteAfterPeriod` | Автоудаление после периода | **true** | fail-safe отчётности |
| `ad.contractorId` | Выбор из справочника | last used | autocomplete по ИНН |

Площадки маркировки (паритет help): **VK, OK, Telegram, Rutube, Max**. Истории — см. `scheduled-stories.md`.

### 3.5. Список «Запланированные»

Фильтры (паритет `posts.getList`): канал, сеть, active/paused, recurrence, status, search, date_from/to, sort.  
Действия: edit, pause, resume, delete, duplicate, open remote URL, retry failed publication.

---

## 4. Матрица поддержки по сетям

| Возможность | VK | Telegram | OK | Max | Rutube |
|---|---|---|---|---|---|
| Текст + медиа | ✔ | ✔ | ✔ | ✔ | видео |
| Форматирование | plain | HTML | plain | HTML/MD | plain |
| Нативный schedule API | `publish_date` (не используем в MVP — свой конвейер) | нет | `publishAt` | unknown | unknown |
| Pin / unpin | ✔ | ✔ | unknown | ✔ | нет |
| Delete | ✔ | ✔ | ✔ | ✔ | ✔ |
| Close comments | ✔ | — | ✔ | — | — |
| Mute | ✔ | ✔ | ✔ | ✔ | — |
| Repost | ✔ | ✔ | ✔ | нет | нет |
| First comment | ✔ | discussion ★ | unknown | unknown | — |
| Buttons | action_button | inline | — | inline | — |
| Poll | ✔ | ✔ | ✔ | — | — |
| Donut / co-authors | ✔ | — | — | — | — |
| Маркировка ОРД | ✔ | ✔ (текст) | ✔ | ✔ | ✔ |
| Идемпотентность | `guid` | findExisting | hash±2m | hash±2m | hash |

Лимиты — `network-capabilities.md` (дата проверки 2026-11-17).

---

## 5. Модель данных

Расширения к ядру BE-2 (не ломая существующие таблицы):

```text
posts
  + content_kind          -- 'post' | 'story' (default post)
  + is_ad                 -- bool (уже в схеме)
  + ord_creative_id       -- FK nullable
  + ad_payload            -- jsonb (снимок ads_items на момент планирования)
  + stop_at               -- timestamptz
  + remove_after_publish  -- bool

post_variants.options jsonb  -- pin, unpinAt, autoDelete, signed, mute, closeComments,
                             -- donutDays, coOwnerIds, primaryAttachmentsMode, buttons, ...

ord_connections(id, workspace_id, provider /*vk_ord|yandex_ord|ozon_ord*/,
  api_key_enc, publisher_inn, publisher_name, org_type, phone, roles jsonb,
  status, created_at)
ord_contractors(id, workspace_id, inn, kpp, name, org_type, country_oksm, meta jsonb, unique(workspace_id, inn, kpp))
ord_contracts(id, workspace_id, contractor_id, external_id, serial, date_start, date_end,
  amount, vat_rate, provider, provider_ref, status)
ord_creatives(id, workspace_id, contract_id, post_id, external_id, erid, provider,
  kktu text[], media_refs jsonb, status /*draft|registered|error*/,
  registered_at, last_error)
ord_platforms(...), ord_invoices(...), ord_statistics(...)
ord_dictionary_kktu / ord_dictionary_oksm  -- кэш справочников
```

Публикации и `scheduled_actions` (delete/unpin/first_comment) — без изменений контракта конвейера.

---

## 6. Предложение дополнений OpenAPI

Существующие: `/api/v1/posts`, `/schedule`, `/pause`, `/resume`, `/bulk`, `/preview`, `/calendar`, `/publications`.

**Добавить:**

```http
GET    /api/v1/workspaces/{id}/ord/connections
PUT    /api/v1/workspaces/{id}/ord/connections/{provider}
POST   /api/v1/workspaces/{id}/ord/connections/{provider}/test
GET    /api/v1/workspaces/{id}/ord/contractors
POST   /api/v1/workspaces/{id}/ord/contractors
GET    /api/v1/workspaces/{id}/ord/contracts
POST   /api/v1/workspaces/{id}/ord/contracts
GET    /api/v1/ord/dictionaries/kktu
GET    /api/v1/ord/dictionaries/oksm
POST   /api/v1/posts/{id}/ord/register      # идемпотентно создаёт креатив → erid
GET    /api/v1/posts/{id}/ord/status
POST   /api/v1/ord/reports/run              # ручной запуск актов/статистики (админ/owner)
```

Расширить `CreatePostRequest` / `PostOptions`:
`isAd`, `ad` (schema `AdMarking`), `coOwnerIds`, `donutDays`, `stopAt`, `removeAfterPublish`, `poll`, `geo`, `copyrightUrl`, `actionButton`, `repost`.

Коды ошибок: `ord_not_configured`, `ord_validation_failed`, `ord_provider_unavailable`, `ord_erid_missing`, `ad_inn_invalid`, `network_option_unsupported`.

Интерфейс бэка: `OrdProvider` — **полноценные** адаптеры `vk_ord` и `yandex_ord` (не stub); ОЗОН и др. — позже через тот же интерфейс (`ozon_ord` и т.п.). Клиент подключает **свой** API-ключ ОРД; Posts **не субсидирует** ОРД (решение заказчика Q-ORD-03 / Q-ORD-06). См. `02-backend.md` §10 / BE-15.

---

## 7. Состояния и ошибки

| Состояние креатива | Смысл |
|---|---|
| `draft` | Данные заполнены, в ОРД не отправлено |
| `registering` | Идёт PUT креатива |
| `registered` | erid получен |
| `error` | Ошибка ОРД; публикация заблокирована пока isAd |

| Состояние публикации | Дополнительно для рекламы |
|---|---|
| `pending` | Ждёт erid если isAd |
| `failed_permanent` | `ord_*` без ретраев публикации |
| `succeeded` | erid зафиксирован в `publication` meta |

Ретраи ОРД: отдельно от публикации, exponential backoff, алерт Owner при >3 неудачах.

---

## 8. Надёжность

1. Регистрация креатива **идемпотентна** по `external_id = creative:{postId}:{version}` (как ORD VK PUT).
2. Публикация рекламного поста только после `erid != null` (кроме feature-flag `ord.allow_publish_without_erid` — **выкл** по умолчанию).
3. Вставка маркера: для TG/Max — префикс текста; для VK — текст + по возможности меню; для OK — по правилам площадки.
4. Повтор публикации — `guid` / `findExisting`; erid не пересоздаётся.
5. Ежемесячный job (ShedLock, 7-е число 03:00 Europe/Moscow — паритет vposter): акты + statistics; идемпотентность по периоду.
6. Автоудаление — `scheduled_actions`; удаление уже удалённого = success.

---

## 9. RBAC

| Действие | Owner | Admin | Editor | Author | Viewer |
|---|---|---|---|---|---|
| Настроить ORD connection | ✔ | ✔ | — | — | — |
| CRUD contractors/contracts | ✔ | ✔ | ✔ | — | — |
| Включить isAd на посте | ✔ | ✔ | ✔ | — (только черновик без schedule) | — |
| Планировать рекламный пост | ✔ | ✔ | ✔ | — | — |
| Смотреть erid/статус | ✔ | ✔ | ✔ | свои | ✔ (проект) |
| Запуск отчётов ORD | ✔ | ✔ | — | — | — |

Все проверки с `workspace_id`; чужой workspace → 404.

---

## 10. Аудит

События: `ord.connection.updated`, `ord.contractor.upserted`, `ord.creative.registered`, `ord.creative.failed`, `post.ad.enabled`, `ord.report.submitted`. Diff без секретов API-ключей.

---

## 11. Юридические требования

1. Интернет-реклама в РФ подлежит маркировке (ФЗ-38 ст. 18.1): пометка «Реклама», сведения о рекламодателе, токен erid, отчётность в ЕРИР через ОРД.
2. Posts — технический посредник: пользователь подтверждает ответственность за корректность данных (checkbox при первом подключении ORD).
3. Самореклама — отдельный упрощённый поток (без договора с третьим лицом), по правилам выбранного ОРД.
4. Неверные суммы/акты — риск претензий; Posts ★: сверка `posts_summ` vs акт, предупреждения о расхождениях.
5. Хранение ПДн рекламодателей — 152-ФЗ, только в РФ.

---

## 12. Критерии приёмки

1. Все поля §3 доступны в UI и API; неподдерживаемые сетью — disabled с tooltip.
2. Паритет recurrence enum vposter.
3. При isAd без ORD connection — schedule блокируется.
4. Успешный sandbox ORD VK/Yandex → erid в предпросмотре и в опубликованном тексте.
5. Акт/статистика за тестовый период создаются job’ом или ручным run.
6. Автоудаление рекламного поста срабатывает; повтор delete идемпотентен.
7. TC-PUB-* сохраняются для рекламных постов (0 дублей).
8. Репост+маркировка на VK/OK.
9. IDOR/RBAC по матрице §9 зелёные.
10. Документированы отличия ★ (варианты, календарь, сегментные тарифы, сверка актов).

Тест-кейсы: `docs/qa/test-cases/E10-ad-marking.md`.

---

## 13. Преимущества Posts ★

1. Варианты текста/медиа под сеть + живой предпросмотр с маркером «Реклама».
2. Командный RBAC и аудит изменений ORD/рекламных постов.
3. Конвейер с SLA и идемпотентностью (отзывы на vposter — «сгоревшие» посты).
4. Справочник контрагентов на workspace + автозаполнение по ИНН + сверка сумм актов.
5. Несколько ORD-провайдеров за единым UI (`OrdProvider`): на старте VK + Яндекс полноценно; расширение (ОЗОН…) без смены UX.
6. Нет лимита «25 постов на одно время» — масштаб под 2 000 pub/s с rate-limit сетей.
7. Календарь и bulk вместо только списка.

---

## 14. Допущения и решения

**Решения заказчика (сняты ASSUMPTION):**
- **Q-ORD-03:** VK ОРД и ОРД Яндекс — оба полноценно; ОЗОН/другие — позже через `OrdProvider`.
- **Q-ORD-06:** клиентский ключ ОРД; Posts не субсидирует стоимость ОРД.

**Открытые допущения** — см. `parity-open-questions.md` (Q-POST-*, Q-ORD-01/02/04/05): автоудаление рекламы по умолчанию **вкл**; период отчётности 30 дней.

## 15. История изменений

| Версия | Дата | Изменение |
|---|---|---|
| 1.0.0 | 2026-11-17 | Первая полная спецификация отложенных постов + ОРД |
| 1.0.1 | 2026-11-17 | Q-ORD-03/06: VK+Яндекс полноценно; клиентский ключ; ОЗОН позже |
