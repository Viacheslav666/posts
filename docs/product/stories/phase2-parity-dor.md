# Definition of Ready — P0 stories паритета (Фаза 2)

| Поле | Значение |
|---|---|
| Задача | **AN-5** |
| Дата | 2026-11-17 |
| DoR (эталон) | `docs/specs/01-analyst.md` §10 |
| Спеки | `features/scheduled-posts-and-ad-marking.md` v1.0.1 · `scheduled-stories.md` v1.0.1 · `grabber.md` v1.0.1 |
| Решения заказчика | `parity-open-questions.md` §0 — **не менять** |

Чеклист DoR для story: ценность · AC Given/When/Then · приоритет · зависимости · экран/N/A · оценка Бэк/Фронт (груминг) · тестовые данные QA.

Ниже — сводка по **P0 паритета** (E10 / E11 / E17). Issues создаёт владелец: `./infra/scripts/gh-stories-issues.sh --apply`.

---

## Сводка DoR

| Story | Ценность | AC G/W/T | Prio | Deps | Экран | Задачи | TC | Оценка B/F | QA data |
|---|:---:|:---:|:---:|:---:|:---:|---|---|:---:|:---:|
| [US-10.1](./E10-US-10.1-ord-connection-client-key.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-15, FE-12, QA-10 | TC-AD-03,09,15 | груминг | черновик |
| [US-10.2](./E10-US-10.2-ord-contractors-contracts-dicts.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-15, FE-12, QA-10 | TC-AD-11,14,16 | груминг | черновик |
| [US-10.3](./E10-US-10.3-ad-marking-erid-fail-closed.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-15, FE-12, QA-10 | TC-AD-03,04,07,08,10,12,13 | груминг | черновик |
| [US-10.4](./E10-US-10.4-ord-reports-autodelete.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-15, FE-12, QA-10 | TC-AD-05,06 | груминг | черновик |
| [US-11.1](./E11-US-11.1-schedule-story-vk.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-16, FE-13, QA-11 | TC-STORY-01,15,17,30 | груминг | черновик |
| [US-11.2](./E11-US-11.2-schedule-story-ok.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-16, FE-13, QA-11 | TC-STORY-02,03,07 | груминг | черновик |
| [US-11.3](./E11-US-11.3-vk-story-connect.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-16, FE-13, QA-11 | TC-STORY-06,20 | груминг | черновик |
| [US-11.4](./E11-US-11.4-story-ttl-link-recurrence.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-16, FE-13, QA-11 | TC-STORY-03…05,08,09,14,23 | груминг | черновик |
| [US-11.6](./E11-US-11.6-story-ad-marking.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-15/16, FE-13, QA-11 | TC-STORY-11,12,24 | груминг | черновик |
| [US-11.7](./E11-US-11.7-stories-networks-out-of-scope.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-16, FE-13, QA-11 | TC-STORY-13,27–29 | груминг | черновик |
| [US-17.1](./E17-US-17.1-grabber-rule-vk.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-17, FE-14, QA-12 | TC-GRAB-01…04,10,22 | груминг | черновик |
| [US-17.2](./E17-US-17.2-grabber-moderate-default.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-17, FE-14, QA-12 | TC-GRAB-08,08b,09,11,15,23,27 | груминг | черновик |
| [US-17.3](./E17-US-17.3-grabber-dedup-attribution.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-17, FE-14, QA-12 | TC-GRAB-05…07,13,14 | груминг | черновик |
| [US-17.4](./E17-US-17.4-rss-ingest.md) | ✔ | ✔ | P0 | ✔ | ✔ | BE-17, FE-14, QA-12 | TC-GRAB-11,12,16 | груминг | черновик |

**Итог аналитика:** все P0 stories паритета соответствуют DoR по полям, которые заполняет Аналитик. Оценка story points Бэк/Фронт и финальное утверждение тестовых данных — на груминге с BE/FE/QA (как для E1–E6).

Связанные P1 паритета (не блокируют DoR P0, но в том же пакете): US-10.5, US-11.5, US-17.5.

---

## Инварианты приёмки (все P0)

1. **Q-ORD-03/06:** оба ОРД полноценны; ключ клиента; нет субсидии Posts.
2. **Fail-closed:** нет erid → нет публикации рекламы (пост и история).
3. **Q-STORY-05:** Instagram отсутствует в продукте историй.
4. **Q-GRAB-02:** default и обязательный продуктовый режим — `moderate`; `auto` только явно.
5. Публикации ads/stories/ingest — только через конвейер E5 (0 дублей / 0 потерь).

---

## Трассировка на тест-кейсы

| Эпик | Файл TC | Автоматизация |
|---|---|---|
| E10 | [`docs/qa/test-cases/E10-ad-marking.md`](../../qa/test-cases/E10-ad-marking.md) | QA-10 |
| E11 | [`docs/qa/test-cases/E11-stories.md`](../../qa/test-cases/E11-stories.md) | QA-11 |
| E17 | [`docs/qa/test-cases/E17-grabber.md`](../../qa/test-cases/E17-grabber.md) | QA-12 |
