# Тест-план MVP - Posts

| | |
|---|---|
| Версия | 1.0 |
| Задача | **QA-4** |
| Статус | Готов к ревью (Аналитик, Бэк, Фронт) |
| Ветка | `docs/qa-4-test-plan` |
| Источники | `docs/specs/05-qa.md`, `docs/qa/test-strategy.md`, `docs/product/PRD-MVP.md`, stories E1–E6, `roles-permissions.yaml`, `network-capabilities.md`, `billing-rules.md` |
| Кейсы | [`test-cases/`](test-cases/) |
| Данные | [`test-data.md`](test-data.md), `tests/seed/` |
| Фикстуры форматирования | `docs/api/fixtures/formatting/` (≥ 50) |

## 1. Цель и объект тестирования

Подтвердить готовность **Фазы 1 (MVP)** к гейту релиза: надёжный планировщик для команды с публикациями **без потерь и дублей**, p99 ≤ 60 с при пике 2 000 пуб./с, RBAC/IDOR, каналы VK/TG/OK/Max, редактор с вариантами, биллинг и уведомления.

**Главный риск:** потеря или дубль публикации (Blocker по [`defect-classification.md`](defect-classification.md)).

## 2. Scope

### 2.1. In scope (MVP)

| Эпик | Содержание | Кейсы |
|---|---|---|
| **E1** Аккаунт и вход | Email, OAuth VK/Яндекс, 2FA, recovery, сессии, TZ | [`E1.md`](test-cases/E1.md) TC-AUTH-* |
| **E2** Workspace / команда | Workspace, проекты, приглашения, аудит, RBAC/IDOR | [`E2.md`](test-cases/E2.md) TC-RBAC-*, TC-IDOR-*, TC-WS-* |
| **E3** Каналы | Подключение VK/TG/OK/Max, статусы, наборы, лимиты тарифа | [`E3.md`](test-cases/E3.md) TC-CH-* |
| **E4** Редактор и планирование | Варианты, медиа, форматирование, настройки, черновики, календарь, bulk, статусы, репост | [`E4.md`](test-cases/E4.md) TC-POST-* |
| **E5** Надёжность и уведомления | SLA, ретраи, идемпотентность, уведомления | [`E5.md`](test-cases/E5.md) TC-PUB-*, TC-NOTIF-* |
| **E6** Биллинг | Планы, триал, ЮKassa, grace/freeze, промо (кейсы - QA-5+/спринт биллинга; ссылки в §8) | TC-BILL-* (черновик в стратегии) |
| **NFR** | Нагрузка L1–L6, хаос/DR, security, a11y | `05-qa.md` §6–8; исполнение QA-7…QA-9 |

### 2.2. Out of scope (явно)

Фаза 2–3: ОРД, инбокс, AI-студия, Rutube, истории, импорт из Впостера, публичное API, мобильные приложения - по `PRD-MVP.md` §5. Полноценный workflow согласования (E8) - только задел Author → «на согласовании».

## 3. Окружения и данные

| Окружение | Что гоняем | Данные |
|---|---|---|
| **local** | unit/integration, smoke e2e, фикстуры форматирования | seed profile `smoke`; Mailpit; эмулятор/WireMock |
| **CI** | unit, integration, ArchUnit, contract, lint | эфемерные Testcontainers |
| **staging** | e2e полный, L*, хаос, security, story-приёмка | seed `load` (1 000 каналов / 50 000 постов) + реальные тестовые каналы для smoke |
| **prod** | только синтетика 30 мин | выделенные тестовые каналы платформы |

Подробности seed - [`test-data.md`](test-data.md). Привязка seed → SQL: **TODO после BE-2**.

## 4. Критерии входа / выхода

Наследуют [`test-strategy.md`](test-strategy.md) §6.

**Вход в тест story:** AC согласованы; деплой staging (или compose); unit/integration зоны зелёные; есть TC со ссылкой на AC.

**Выход story:** 100 % P0 TC по story; 0 Blocker/Critical; Major - по согласованию владельца.

**Гейт релиза MVP:**

| Критерий | Порог |
|---|---|
| P0-кейсы E1–E6 | 100 % пройдено |
| Дефекты | 0 Blocker/Critical; ≤ 5 Major с обходом |
| L2 пик 2 000/с | p99 ≤ 60 с; **0 потерь, 0 дублей** |
| Хаос TC-PUB-06/07/08/10 | 0 потерь, 0 дублей |
| Restore бэкапа | отрепетирован; RPO ≤ 5 мин |
| Security / a11y | ZAP без Critical; IDOR P0 без провалов; axe без critical |
| Отчёт | `docs/qa/release-report-vX.md` |

## 5. Приоритеты и автоматизация

| Приоритет кейса | Правило | Автоматизация к MVP |
|---|---|---|
| **P0** | Все AC stories P0; TC-PUB-*; IDOR; лимиты тарифа каналов | цель ≥ 90 % авто (`test-strategy`) |
| **P1** | Граничные UX, CSV-аудит, редкие OAuth-ошибки | кандидат в авто / ручной exploratory |
| **P2** | Косметика, копирайт | ручной |

В каждом TC поле **Автоматизация:** `авто` | `ручной` | `кандидат в авто`.

## 6. Трассируемость

```
Story (US-x.y) → AC# → TC-ID → автотест (путь) → дефект
```

Матрицы покрытия - в файлах `test-cases/E*.md` (раздел «Трассировка»). **Приёмка QA-4:** каждый P0 AC E1–E5 покрыт ≥ 1 кейсом.

ID-пространства: `TC-AUTH`, `TC-RBAC`, `TC-IDOR`, `TC-WS`, `TC-CH`, `TC-POST`, `TC-PUB`, `TC-NOTIF`, `TC-BILL` (E6).

## 7. Риски плана

| Риск | Митигация |
|---|---|
| Схема БД ещё не слита (BE-2) | Seed - логический JSON + TODO маппинга |
| Эмулятор ≠ прод API | Smoke на реальных каналах; контрактные WireMock |
| Объём P0 AC | Матрица в E*.md; пробелы закрывать в спринте фичи |
| Flaky публикаций | Awaitility / WS; запрет голых sleep |
| Нагрузка дорогая | Урезанный L1+L2 weekly; полный L* перед релизом |

## 8. План исполнения (связь с agent-tasks)

| Этап | Задача | Результат |
|---|---|---|
| Нед. 2 (сейчас) | **QA-4** | этот план, TC E1–E5, seed, formatting ≥ 50 |
| Спринт 1–2 | QA-2, QA-3 | Playwright/k6/Schemathesis; эмулятор |
| Спринт 3–4 | QA-5 | авто TC-PUB-* |
| Спринт 4–5 | QA-6, billing TC | RBAC-генерация; TC-BILL-* |
| Нед. 8–10 | QA-7…9 | L1–L6, хаос, security, release-report |

## 9. Артефакты DoD этого плана

- [x] `test-plan-mvp.md`
- [x] `test-cases/E1.md` … `E5.md` с авто/ручной и трассировкой на stories/AC
- [x] `test-data.md` + `tests/seed/` (spec + генератор; TODO BE-2)
- [x] `docs/api/fixtures/formatting/` ≥ 50 кейсов
- [x] Обновлён `docs/qa/README.md`

## 10. История

| Версия | Дата | Изменение |
|---|---|---|
| 1.0 | 2026-11-17 | QA-4: первая версия тест-плана MVP |
