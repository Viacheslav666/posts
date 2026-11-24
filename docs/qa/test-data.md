# Тестовые данные MVP

| | |
|---|---|
| Задача | **QA-4** |
| Спецификация seed | [`tests/seed/seed_spec.yaml`](../../tests/seed/seed_spec.yaml) (+ JSON-копия) |
| Генератор | [`tests/seed/generate_seed.py`](../../tests/seed/generate_seed.py) |
| Привязка к БД | **TODO после BE-2** (схема домена ещё не стабилизирована; сейчас только `V1__init.sql` / `schema_info`) |

## 1. Цели набора

1. Пользователи **всех ролей** MVP: Owner, Admin, Editor, Author, Viewer (+ foreign Owner для IDOR, unconfirmed, 2FA).
2. Workspace нагрузки: **1 000 каналов**, **50 000 постов** (профиль `load`).
3. Изоляция для TC-IDOR: отдельный `ws_foreign`.
4. Воспроизводимость: фиксированный `seed: 424242`.

## 2. Профили генерации

| Профиль | Каналы `ws_load` | Посты `ws_load` | Назначение |
|---|---:|---:|---|
| `smoke` | 8 | 40 | local / быстрый e2e |
| `load` | 1000 | 50000 | staging, L6-подготовка, объёмные UI-листы |
| `idor` | 4 | 10 | фокус на чужом workspace |

```bash
# нужен PyYAML (или используйте seed_spec.json)
python3 tests/seed/generate_seed.py --profile smoke
python3 tests/seed/generate_seed.py --profile load --dry-run   # проверка объёмов без записи
python3 tests/seed/generate_seed.py --profile load --out-dir /tmp/posts-seed-load
```

Выход: логический JSON (`users`, `workspaces`, `projects`, `memberships`, `channels`, `posts`) - **не SQL**.

## 3. Учётные записи (логические)

| key | email | Роль в `ws_load` | Примечание |
|---|---|---|---|
| `owner_load` | qa-owner-load@posts.test | Owner | тариф Агентство + аддоны каналов |
| `admin_main` | qa-admin@posts.test | Admin | |
| `editor_a` | qa-editor-a@posts.test | Editor | grant: `project_alpha`; TZ Asia/Yekaterinburg |
| `editor_b` | qa-editor-b@posts.test | Editor | grant: `project_beta` |
| `author_main` | qa-author@posts.test | Author | grant: `project_alpha` |
| `viewer_main` | qa-viewer@posts.test | Viewer | grant: `project_alpha` |
| `owner_foreign` | qa-owner-foreign@posts.test | Owner `ws_foreign` | IDOR |
| `unconfirmed` | qa-unconfirmed@posts.test | Owner личного | email не подтверждён (US-1.1) |
| `totp_user` | qa-2fa@posts.test | Owner | 2FA включена (US-1.3) |

Пароли в seed - плейсхолдер `SEED_ONLY_CHANGE_ME`; реальные секреты только в Vault/ENV стенда.

## 4. Объёмы и смеси

- Каналы: VK 40 % / TG 30 % / OK 15 % / Max 15 %; статусы active 90 % / reconnect 5 % / disabled 5 %.
- Посты: draft 15 % / scheduled 45 % / published 30 % / failed 5 % / paused 5 %.
- Варианты под сеть включаются (`include_variants: true`).

Лимит тарифа Агентства для 1000 каналов: базовые 100 + пакеты +25 (см. `billing-rules.md`); в spec - `channel_addons: 36`.

## 5. TODO после BE-2

1. Зафиксировать маппинг logical → таблицы Flyway (`users`, `workspaces`, `workspace_members`, `projects`, `project_grants`, `channels`, `posts`, `post_variants`, `publications`, …).
2. Добавить `tests/seed/mappers/be2_sql.py` (или JDBC loader в `backend/libs/test-support`).
3. Опционально Flyway callback / `R__seed_qa.sql` только для profile `local`/`staging`.
4. Не коммитить дамп `load` (десятки МБ) - генерировать на стенде.

До выполнения пункта 1 генератор **намеренно** не пишет SQL.

## 6. Связь с кейсами

| Набор | Кейсы |
|---|---|
| Роли + grants | TC-RBAC-*, TC-IDOR-01, TC-WS-* |
| `unconfirmed` / `totp_user` | TC-AUTH-* |
| 1000 / 50000 | TC-POST-11 (bulk), подготовка L6, календарь под нагрузкой |
| `ws_foreign` | TC-IDOR-01, TC-RBAC foreign workspace → 404 |
