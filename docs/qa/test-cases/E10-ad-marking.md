# E10 — Маркировка рекламы / ОРД (TC-AD)

| | |
|---|---|
| Задача | QA-10 (после AN-P) |
| Спека | `docs/product/features/scheduled-posts-and-ad-marking.md` |
| Stories | E10 + расширения US-4.4 |

## Трассировка AC → TC

| AC# | Кратко | TC | Автоматизация | Автотест (QA-10) |
|---|---|---|---|---|
| 1 | Поля редактора паритет | TC-AD-01 | авто | e2e `ad-marking.spec.ts` + FE unit |
| 2 | Recurrence enum | TC-AD-02 | авто | `OrdAdMarkingIT` + FE + e2e |
| 3 | isAd без ORD → блок | TC-AD-03 | авто | `OrdAdMarkingIT` + FE + e2e |
| 4 | erid sandbox | TC-AD-04 | авто (WireMock ORD) | `OrdAdMarkingIT` + `OrdAdPipelineIT` + e2e |
| 5 | Акт/статистика | TC-AD-05 | авто | `OrdAdMarkingIT#reportRunIdempotent` |
| 6 | Автоудаление рекламы | TC-AD-06 | авто | `OrdAdPipelineIT#tcAd06AutoDeleteAfterPeriod` |
| 7 | 0 дублей рекламного поста | TC-AD-07 | авто | `OrdAdPipelineIT#tcAd07RetryNoDuplicate` |
| 8 | Репост+маркировка VK/OK | TC-AD-08 | авто | `OrdAdMarkingIT#repostWithAdMarking` |
| 9 | RBAC ORD | TC-AD-09 | авто | `OrdAdMarkingIT#rbacAuthorAndViewerDenied` |
| 10 | ★ предпросмотр маркера | TC-AD-10 | авто | `OrdAdMarkingIT#previewShowsMarker` |
| — | Невалидный ИНН | TC-AD-11 | авто | `OrdAdMarkingIT` + validator unit |
| — | ORD 5xx/429/timeout → fail-closed | TC-AD-12 | авто | `OrdAdMarkingIT` + `OrdAdPipelineIT` + provider WM |
| — | Идемпотентность creative external_id | TC-AD-13 | авто | `OrdAdMarkingIT` + provider WM |
| — | Самореклама | TC-AD-14 | авто | `OrdAdMarkingIT#selfPromoSkipsContract` |
| — | Смена провайдера VK↔Yandex | TC-AD-15 | ручной / авто | `OrdAdMarkingIT#yandexConnectionTest` |
| — | ККТУ/ОКСМ справочники | TC-AD-16 | авто | `OrdAdMarkingIT#dictionariesAvailable` |
| — | Donut/co-authors/poll UI | TC-AD-17 | авто | `OrdAdMarkingIT#postOptionsParity` |
| — | IDOR чужой creative | TC-AD-18 | авто | `OrdAdMarkingIT#foreignCreativeIdor` |

Полная матрица: [`docs/qa/traceability-tc-ad.md`](../traceability-tc-ad.md).

---

## TC-AD-01 — Все опции поста отображаются с учётом сети

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Для выбранных каналов доступны только опции из матрицы спеки; Donut/соавторы только VK; кнопки — TG/Max.

---

## TC-AD-02 — Recurrence соответствует enum vposter

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Каждый `post_day` из API vposter маппится в `recurrence`; материализация publications на 14 дней.

---

## TC-AD-03 — Маркировка без ORD connection блокирует schedule

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Код | `ord_not_configured` |

---

## TC-AD-04 — Успешная регистрация креатива → erid в тексте

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Двойник | WireMock ORD VK + Yandex |

### Ожидание
После `POST .../ord/register` у креатива есть erid; preview и публикация содержат «Реклама», имя/ИНН, `erid:`.

---

## TC-AD-05 — Ежемесячный отчёт / ручной run

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
Job или `POST /ord/reports/run` создаёт акт+statistics за период; повторный вызов идемпотентен.

---

## TC-AD-06 — Автоудаление после периода показа

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

### Ожидание
`scheduled_actions` kind=delete выполняется; повтор — success.

---

## TC-AD-07 — Рекламный пост: 0 дублей при ретрае

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Связь | TC-PUB-02/03/06 |

---

## TC-AD-08 — Репост VK/OK с маркировкой

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

---

## TC-AD-09 — RBAC: Author не планирует рекламу; Viewer не настраивает ORD

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

---

## TC-AD-10 — Предпросмотр показывает маркер до публикации

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | **авто** |

---

## TC-AD-11 — Невалидный ИНН → 400 `ad_inn_invalid`

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

---

## TC-AD-12 — ORD 5xx → публикация не уходит в сеть

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

---

## TC-AD-13 — Повтор register не меняет erid

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

---

## TC-AD-14 — Самореклама без договора с третьим лицом

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | **авто** |

---

## TC-AD-15 — Переключение провайдера ORD

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | ручной + авто API |

---

## TC-AD-16 — Справочники ККТУ и ОКСМ отдаются API

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | **авто** |

---

## TC-AD-17 — Donut, соавторы, опрос сохраняются в options

| Поле | Значение |
|---|---|
| Приоритет | P1 |
| **Автоматизация** | **авто** |

---

## TC-AD-18 — IDOR к чужому ord_creative → 404

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |

---

## TC-AD-19 — Шесть режимов размещения + предупреждение vk_menu (FE-15)

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Автотест | e2e `ad-marking.spec.ts` · Vitest `ad-marker-preview` / `ad-marking-form` |

### Ожидание
В форме доступны `vk_menu`, `text_start`, `text_end`, `vk_menu_and_text_end`, `click_link_start`, `click_link_end`; для `vk_menu*` показывается `vk_menu_unsupported` (текст в конце поста); есть превью, ставка НДС и ссылка АРИР.

---

## TC-AD-20 — Настройки ОРД: адрес, роли, оферта (FE-15)

| Поле | Значение |
|---|---|
| Приоритет | P0 |
| **Автоматизация** | **авто** |
| Автотест | e2e `ad-marking.spec.ts` |

### Ожидание
В форме подключения: адрес*, ставка НДС, роли (рекламодатель/издатель), уведомления о проблемах, ссылка ЛК ЕРИР, скачивание `/legal/ord-offer-template.docx` (черновик).
