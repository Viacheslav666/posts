# User stories Posts

| Поле | Значение |
|---|---|
| MVP | **AN-3** — E1–E6 |
| Фаза 2 | **AN-5** — E8–E22 (`docs/an-5-phase2-stories`) |
| Формат AC | Given / When / Then |
| DoR | `docs/specs/01-analyst.md` §10 |
| DoR P0 паритета | [`phase2-parity-dor.md`](./phase2-parity-dor.md) |
| PRD | [`../PRD-MVP.md`](../PRD-MVP.md) · [`../PRD-phase2.md`](../PRD-phase2.md) |
| Биллинг | [`../billing-rules.md`](../billing-rules.md) |

Каждый файл — одна user story с ценностью, AC, приоритетом, зависимостями, экраном, черновиком тестовых данных и чеклистом DoR.

Issues в GitHub **не создаются агентом** — владелец запускает:

```bash
./infra/scripts/gh-stories-issues.sh          # dry-run по умолчанию
./infra/scripts/gh-stories-issues.sh --apply  # создать/обновить issues
```

---

## Сводка MVP (E1–E6)

| Эпик | Stories | P0 | P1 |
|---|---:|---:|---:|
| [E1 Аккаунт и вход](#e1) | 6 | 6 | 0 |
| [E2 Workspace, проекты, команда](#e2) | 4 | 4 | 0 |
| [E3 Каналы](#e3) | 7 | 7 | 0 |
| [E4 Создание и планирование](#e4) | 9 | 9 | 0 |
| [E5 Надёжность и уведомления](#e5) | 4 | 4 | 0 |
| [E6 Биллинг](#e6) | 6 | 5 | 1 |
| **Итого MVP** | **36** | **35** | **1** |

## Сводка Фаза 2 (E8–E22)

| Эпик | Stories | P0 | P1 | P2 |
|---|---:|---:|---:|---:|
| [E8 Согласование](#e8) | 3 | 2 | 1 | 0 |
| [E9 Рубрики и слоты](#e9) | 2 | 0 | 2 | 0 |
| [E10 ОРД / маркировка](#e10) ★паритет | 5 | 4 | 1 | 0 |
| [E11 Истории](#e11) ★паритет | 7 | 6 | 1 | 0 |
| [E12 Rutube](#e12) | 2 | 0 | 2 | 0 |
| [E13 Аналитика](#e13) | 2 | 1 | 1 | 0 |
| [E14 Инбокс](#e14) | 2 | 1 | 1 | 0 |
| [E15 AI-студия](#e15) | 3 | 0 | 2 | 1 |
| [E16 Кросспостинг](#e16) | 1 | 0 | 1 | 0 |
| [E17 Граббер / RSS](#e17) ★паритет | 5 | 4 | 1 | 0 |
| [E18 Предложка](#e18) | 1 | 0 | 1 | 0 |
| [E19 Антиспам](#e19) | 1 | 0 | 1 | 0 |
| [E20 API / webhooks](#e20) | 2 | 0 | 2 | 0 |
| [E21 Партнёрка](#e21) | 1 | 0 | 0 | 1 |
| [E22 Импорт vposter](#e22) | 1 | 0 | 1 | 0 |
| **Итого Фаза 2** | **38** | **18** | **18** | **2** |

★паритет = критический путь AN-P → BE-15…17 / FE-12…14 / QA-10…12.

---

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

### E8

| ID | Файл | Приоритет |
|---|---|---|
| US-8.1 | [E8-US-8.1-approval-workflow.md](./E8-US-8.1-approval-workflow.md) | P0 |
| US-8.2 | [E8-US-8.2-client-share-link.md](./E8-US-8.2-client-share-link.md) | P0 |
| US-8.3 | [E8-US-8.3-post-comments-versions.md](./E8-US-8.3-post-comments-versions.md) | P1 |

### E9

| ID | Файл | Приоритет |
|---|---|---|
| US-9.1 | [E9-US-9.1-content-rubrics.md](./E9-US-9.1-content-rubrics.md) | P1 |
| US-9.2 | [E9-US-9.2-content-slots.md](./E9-US-9.2-content-slots.md) | P1 |

### E10

Спека: [`../features/scheduled-posts-and-ad-marking.md`](../features/scheduled-posts-and-ad-marking.md). Задачи: **BE-15**, **FE-12**, **QA-10**. TC: [`../../qa/test-cases/E10-ad-marking.md`](../../qa/test-cases/E10-ad-marking.md).

| ID | Файл | Приоритет |
|---|---|---|
| US-10.1 | [E10-US-10.1-ord-connection-client-key.md](./E10-US-10.1-ord-connection-client-key.md) | P0 |
| US-10.2 | [E10-US-10.2-ord-contractors-contracts-dicts.md](./E10-US-10.2-ord-contractors-contracts-dicts.md) | P0 |
| US-10.3 | [E10-US-10.3-ad-marking-erid-fail-closed.md](./E10-US-10.3-ad-marking-erid-fail-closed.md) | P0 |
| US-10.4 | [E10-US-10.4-ord-reports-autodelete.md](./E10-US-10.4-ord-reports-autodelete.md) | P0 |
| US-10.5 | [E10-US-10.5-post-options-parity-donut-poll.md](./E10-US-10.5-post-options-parity-donut-poll.md) | P1 |

### E11

Спека: [`../features/scheduled-stories.md`](../features/scheduled-stories.md). Задачи: **BE-16**, **FE-13**, **QA-11**. TC: [`../../qa/test-cases/E11-stories.md`](../../qa/test-cases/E11-stories.md). Instagram вне scope (Q-STORY-05).

| ID | Файл | Приоритет |
|---|---|---|
| US-11.1 | [E11-US-11.1-schedule-story-vk.md](./E11-US-11.1-schedule-story-vk.md) | P0 |
| US-11.2 | [E11-US-11.2-schedule-story-ok.md](./E11-US-11.2-schedule-story-ok.md) | P0 |
| US-11.3 | [E11-US-11.3-vk-story-connect.md](./E11-US-11.3-vk-story-connect.md) | P0 |
| US-11.4 | [E11-US-11.4-story-ttl-link-recurrence.md](./E11-US-11.4-story-ttl-link-recurrence.md) | P0 |
| US-11.5 | [E11-US-11.5-stories-batch-upload.md](./E11-US-11.5-stories-batch-upload.md) | P1 |
| US-11.6 | [E11-US-11.6-story-ad-marking.md](./E11-US-11.6-story-ad-marking.md) | P0 |
| US-11.7 | [E11-US-11.7-stories-networks-out-of-scope.md](./E11-US-11.7-stories-networks-out-of-scope.md) | P0 |

### E12

| ID | Файл | Приоритет |
|---|---|---|
| US-12.1 | [E12-US-12.1-connect-rutube.md](./E12-US-12.1-connect-rutube.md) | P1 |
| US-12.2 | [E12-US-12.2-schedule-rutube-video.md](./E12-US-12.2-schedule-rutube-video.md) | P1 |

### E13

| ID | Файл | Приоритет |
|---|---|---|
| US-13.1 | [E13-US-13.1-cross-network-analytics.md](./E13-US-13.1-cross-network-analytics.md) | P0 |
| US-13.2 | [E13-US-13.2-scheduled-pdf-xlsx-reports.md](./E13-US-13.2-scheduled-pdf-xlsx-reports.md) | P1 |

### E14

| ID | Файл | Приоритет |
|---|---|---|
| US-14.1 | [E14-US-14.1-unified-inbox.md](./E14-US-14.1-unified-inbox.md) | P0 |
| US-14.2 | [E14-US-14.2-inbox-assign-templates.md](./E14-US-14.2-inbox-assign-templates.md) | P1 |

### E15

| ID | Файл | Приоритет |
|---|---|---|
| US-15.1 | [E15-US-15.1-ai-text-adapt.md](./E15-US-15.1-ai-text-adapt.md) | P1 |
| US-15.2 | [E15-US-15.2-ai-content-plan.md](./E15-US-15.2-ai-content-plan.md) | P1 |
| US-15.3 | [E15-US-15.3-ai-rewrite-grabber.md](./E15-US-15.3-ai-rewrite-grabber.md) | P2 |

### E16

| ID | Файл | Приоритет |
|---|---|---|
| US-16.1 | [E16-US-16.1-crosspost-rules.md](./E16-US-16.1-crosspost-rules.md) | P1 |

### E17

Спека: [`../features/grabber.md`](../features/grabber.md). Задачи: **BE-17** (+ BE-17b), **FE-14**, **QA-12**. TC: [`../../qa/test-cases/E17-grabber.md`](../../qa/test-cases/E17-grabber.md). Default `moderate` (Q-GRAB-02).

| ID | Файл | Приоритет |
|---|---|---|
| US-17.1 | [E17-US-17.1-grabber-rule-vk.md](./E17-US-17.1-grabber-rule-vk.md) | P0 |
| US-17.2 | [E17-US-17.2-grabber-moderate-default.md](./E17-US-17.2-grabber-moderate-default.md) | P0 |
| US-17.3 | [E17-US-17.3-grabber-dedup-attribution.md](./E17-US-17.3-grabber-dedup-attribution.md) | P0 |
| US-17.4 | [E17-US-17.4-rss-ingest.md](./E17-US-17.4-rss-ingest.md) | P0 |
| US-17.5 | [E17-US-17.5-website-ingest-ssrf.md](./E17-US-17.5-website-ingest-ssrf.md) | P1 |

### E18

| ID | Файл | Приоритет |
|---|---|---|
| US-18.1 | [E18-US-18.1-vk-suggests-autopost.md](./E18-US-18.1-vk-suggests-autopost.md) | P1 |

### E19

| ID | Файл | Приоритет |
|---|---|---|
| US-19.1 | [E19-US-19.1-antispam-vk-tg.md](./E19-US-19.1-antispam-vk-tg.md) | P1 |

### E20

| ID | Файл | Приоритет |
|---|---|---|
| US-20.1 | [E20-US-20.1-public-api-keys.md](./E20-US-20.1-public-api-keys.md) | P1 |
| US-20.2 | [E20-US-20.2-webhooks.md](./E20-US-20.2-webhooks.md) | P1 |

### E21

| ID | Файл | Приоритет |
|---|---|---|
| US-21.1 | [E21-US-21.1-partner-program.md](./E21-US-21.1-partner-program.md) | P2 |

### E22

| ID | Файл | Приоритет |
|---|---|---|
| US-22.1 | [E22-US-22.1-vposter-import.md](./E22-US-22.1-vposter-import.md) | P1 |

---

## Зависимости

### MVP (критический путь онбординга)

```
US-1.1 → US-2.1 → US-2.2 → US-3.* → US-4.* → US-5.*
                ↘ US-6.1 → US-6.2/6.3/6.5/6.6
US-1.2 / US-1.3 / US-1.4 / US-1.5 / US-1.6  (параллельно после US-1.1)
```

### Фаза 2 (паритет)

```
US-10.1 → US-10.2 → US-10.3 → US-10.4
                              ↘ US-11.6
US-11.3 → US-11.1 → US-11.2 / US-11.4 / US-11.7
US-17.1 → US-17.2 → US-17.3 → US-17.4 → US-17.5(P1)
US-8.1 → US-8.2
E22 после стабилизации E10–E17 (Q-MISC-04)
```

---

## Тестируемость

- MVP P0: QA-4 / TC-AUTH…PUB.
- Паритет P0: TC-AD-*, TC-STORY-*, TC-GRAB-* уже в `docs/qa/test-cases/`; автоматизация — QA-10…12.
- Все AC формулированы проверяемо (UI/API/эмулятор/WireMock ORD).
