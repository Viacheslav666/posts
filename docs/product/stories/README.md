# User stories MVP (E1–E6)

| Поле | Значение |
|---|---|
| Задача | **AN-3** |
| Ветка | `docs/an-3-stories-mvp` |
| Формат AC | Given / When / Then |
| DoR | `docs/specs/01-analyst.md` §10 |
| Биллинг | [`../billing-rules.md`](../billing-rules.md) |

Каждый файл — одна user story с ценностью, AC, приоритетом, зависимостями, экраном, черновиком тестовых данных и чеклистом DoR.

Issues в GitHub **не создаются в этом PR** — владелец запускает:

```bash
./infra/scripts/gh-stories-issues.sh          # dry-run по умолчанию
./infra/scripts/gh-stories-issues.sh --apply  # создать/обновить issues
```

## Сводка

| Эпик | Stories | P0 | P1 |
|---|---:|---:|---:|
| [E1 Аккаунт и вход](#e1) | 6 | 6 | 0 |
| [E2 Workspace, проекты, команда](#e2) | 4 | 4 | 0 |
| [E3 Каналы](#e3) | 7 | 7 | 0 |
| [E4 Создание и планирование](#e4) | 9 | 9 | 0 |
| [E5 Надёжность и уведомления](#e5) | 4 | 4 | 0 |
| [E6 Биллинг](#e6) | 6 | 5 | 1 |
| **Итого** | **36** | **35** | **1** |

### E1

| ID | Файл | Приоритет |
|---|---|---|
| US-1.1 | [E1-US-1.1-email-registration.md](./E1-US-1.1-email-registration.md) | P0 |
| US-1.2 | [E1-US-1.2-oauth-vk-yandex.md](./E1-US-1.2-oauth-vk-yandex.md) | P0 |
| US-1.3 | [E1-US-1.3-two-factor-totp.md](./E1-US-1.3-two-factor-totp.md) | P0 |
| US-1.4 | [E1-US-1.4-password-recovery.md](./E1-US-1.4-password-recovery.md) | P0 |
| US-1.5 | [E1-US-1.5-sessions-history.md](./E1-US-1.5-sessions-history.md) | P0 |
| US-1.6 | [E1-US-1.6-user-timezone.md](./E1-US-1.6-user-timezone.md) | P0 |

### E2

| ID | Файл | Приоритет |
|---|---|---|
| US-2.1 | [E2-US-2.1-personal-workspace.md](./E2-US-2.1-personal-workspace.md) | P0 |
| US-2.2 | [E2-US-2.2-projects.md](./E2-US-2.2-projects.md) | P0 |
| US-2.3 | [E2-US-2.3-member-invitations.md](./E2-US-2.3-member-invitations.md) | P0 |
| US-2.4 | [E2-US-2.4-audit-log.md](./E2-US-2.4-audit-log.md) | P0 |

### E3

| ID | Файл | Приоритет |
|---|---|---|
| US-3.1 | [E3-US-3.1-connect-vk.md](./E3-US-3.1-connect-vk.md) | P0 |
| US-3.2 | [E3-US-3.2-connect-telegram.md](./E3-US-3.2-connect-telegram.md) | P0 |
| US-3.3 | [E3-US-3.3-connect-ok.md](./E3-US-3.3-connect-ok.md) | P0 |
| US-3.4 | [E3-US-3.4-connect-max.md](./E3-US-3.4-connect-max.md) | P0 |
| US-3.5 | [E3-US-3.5-channel-status.md](./E3-US-3.5-channel-status.md) | P0 |
| US-3.6 | [E3-US-3.6-channel-sets.md](./E3-US-3.6-channel-sets.md) | P0 |
| US-3.7 | [E3-US-3.7-channel-plan-limits.md](./E3-US-3.7-channel-plan-limits.md) | P0 |

### E4

| ID | Файл | Приоритет |
|---|---|---|
| US-4.1 | [E4-US-4.1-editor-variants.md](./E4-US-4.1-editor-variants.md) | P0 |
| US-4.2 | [E4-US-4.2-media-upload.md](./E4-US-4.2-media-upload.md) | P0 |
| US-4.3 | [E4-US-4.3-formatting.md](./E4-US-4.3-formatting.md) | P0 |
| US-4.4 | [E4-US-4.4-post-settings.md](./E4-US-4.4-post-settings.md) | P0 |
| US-4.5 | [E4-US-4.5-drafts-autosave.md](./E4-US-4.5-drafts-autosave.md) | P0 |
| US-4.6 | [E4-US-4.6-content-calendar.md](./E4-US-4.6-content-calendar.md) | P0 |
| US-4.7 | [E4-US-4.7-bulk-actions.md](./E4-US-4.7-bulk-actions.md) | P0 |
| US-4.8 | [E4-US-4.8-post-publication-statuses.md](./E4-US-4.8-post-publication-statuses.md) | P0 |
| US-4.9 | [E4-US-4.9-repost.md](./E4-US-4.9-repost.md) | P0 |

### E5

| ID | Файл | Приоритет |
|---|---|---|
| US-5.1 | [E5-US-5.1-publish-sla-60s.md](./E5-US-5.1-publish-sla-60s.md) | P0 |
| US-5.2 | [E5-US-5.2-publish-retries.md](./E5-US-5.2-publish-retries.md) | P0 |
| US-5.3 | [E5-US-5.3-publish-idempotency.md](./E5-US-5.3-publish-idempotency.md) | P0 |
| US-5.4 | [E5-US-5.4-notifications.md](./E5-US-5.4-notifications.md) | P0 |

### E6

| ID | Файл | Приоритет |
|---|---|---|
| US-6.1 | [E6-US-6.1-plans-limits.md](./E6-US-6.1-plans-limits.md) | P0 |
| US-6.2 | [E6-US-6.2-trial-pro-7d.md](./E6-US-6.2-trial-pro-7d.md) | P0 |
| US-6.3 | [E6-US-6.3-yookassa-checkout.md](./E6-US-6.3-yookassa-checkout.md) | P0 |
| US-6.4 | [E6-US-6.4-legal-entity-invoice.md](./E6-US-6.4-legal-entity-invoice.md) | P1 |
| US-6.5 | [E6-US-6.5-grace-and-freeze.md](./E6-US-6.5-grace-and-freeze.md) | P0 |
| US-6.6 | [E6-US-6.6-promo-codes.md](./E6-US-6.6-promo-codes.md) | P0 |

## Зависимости (критический путь онбординга)

```
US-1.1 → US-2.1 → US-2.2 → US-3.* → US-4.* → US-5.*
                ↘ US-6.1 → US-6.2/6.3/6.5/6.6
US-1.2 / US-1.3 / US-1.4 / US-1.5 / US-1.6  (параллельно после US-1.1)
```

## Тестируемость

Все P0 AC сформулированы проверяемо (UI/API/нагрузка/хаос). QA-4 строит TC с трассировкой Story → TC-ID. Критичные NFR: US-5.1 (L2), US-5.3 (хаос), RBAC из `roles-permissions.yaml`.
