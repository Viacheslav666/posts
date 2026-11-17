# Отчёт покрытия роль × эндпоинт (QA-6)

Обновлено: 2026-11-17. Генератор: `docs/qa/generate_rbac_coverage_report.py`.

Источник прав: `docs/product/roles-permissions.yaml`. Карта: `docs/qa/rbac-endpoint-map.yaml`.

## Сводка

| Метрика | Значение |
|---|---|
| OpenAPI operationId | 92 |
| В матрице RBAC | 61 |
| Вне RBAC (auth/me/public) | 31 |
| Роли | 5 |
| Ячейки роль×эндпоинт | 305 |
| Реализованы (hint) | 35 |
| Stub / 501 (hint) | 26 — HTTP skip до реализации |

## Матрица (ожидание HTTP)

Легенда: **A** = allow (≠401/403), **D** = deny (403). Foreign workspace → **404** (IDOR).
Эндпоинт со статусом stub автоматически skip в IT, пока ответ 501.

| operationId | action | implemented | owner | admin | editor | author | viewer |
|---|---|---||---|---|---|---|---|
| `billingCheckout` | `billing.manage` | stub | A | D | D | D | D |
| `billingListInvoices` | `billing.view` | stub | A | D | D | D | D |
| `billingApplyPromo` | `billing.apply_promo` | stub | A | D | D | D | D |
| `billingGetSubscription` | `billing.view` | stub | A | D | D | D | D |
| `billingStartTrial` | `billing.manage` | stub | A | D | D | D | D |
| `calendarList` | `calendar.view` | stub | A | A | A | A | A |
| `calendarMove` | `calendar.move` | stub | A | A | A | D | D |
| `channelSetsList` | `channel_sets.view` | yes | A | A | A | A | A |
| `channelSetsCreate` | `channel_sets.manage` | yes | A | A | A | D | D |
| `channelSetsDelete` | `channel_sets.manage` | yes | A | A | A | D | D |
| `channelSetsGet` | `channel_sets.view` | yes | A | A | A | A | A |
| `channelSetsUpdate` | `channel_sets.manage` | yes | A | A | A | D | D |
| `channelsList` | `channels.view` | yes | A | A | A | A | A |
| `channelsConnectCallback` | `channels.manage` | yes | A | A | D | D | D |
| `channelsConnectConfirm` | `channels.manage` | yes | A | A | D | D | D |
| `channelsConnectStart` | `channels.manage` | yes | A | A | D | D | D |
| `channelsDelete` | `channels.manage` | yes | A | A | D | D | D |
| `channelsGet` | `channels.view` | yes | A | A | A | A | A |
| `channelsUpdate` | `channels.manage` | yes | A | A | D | D | D |
| `channelsCheck` | `channels.manage` | yes | A | A | D | D | D |
| `invitationsCancel` | `members.invite` | yes | A | A | D | D | D |
| `mediaList` | `media.view` | yes | A | A | A | A | A |
| `mediaImportUrl` | `media.manage` | yes | A | A | A | A | D |
| `mediaCreateUpload` | `media.manage` | yes | A | A | A | A | D |
| `mediaDelete` | `media.manage` | yes | A | A | A | A | D |
| `mediaGet` | `media.view` | yes | A | A | A | A | A |
| `mediaCompleteUpload` | `media.manage` | yes | A | A | A | A | D |
| `notificationSettingsGet` | `notifications.manage_self` | stub | A | A | A | A | A |
| `notificationSettingsUpdate` | `notifications.manage_self` | stub | A | A | A | A | A |
| `notificationsList` | `notifications.manage_self` | stub | A | A | A | A | A |
| `notificationsMarkRead` | `notifications.manage_self` | stub | A | A | A | A | A |
| `postsList` | `posts.view` | stub | A | A | A | A | A |
| `postsCreate` | `posts.create` | stub | A | A | A | A | D |
| `postsBulk` | `posts.bulk` | stub | A | A | A | D | D |
| `postsPreview` | `posts.view` | stub | A | A | A | A | A |
| `postsDelete` | `posts.delete` | stub | A | A | A | A | D |
| `postsGet` | `posts.view` | stub | A | A | A | A | A |
| `postsUpdate` | `posts.update` | stub | A | A | A | A | D |
| `postsDuplicate` | `posts.create` | stub | A | A | A | A | D |
| `postsPause` | `posts.bulk` | stub | A | A | A | D | D |
| `postsListPublications` | `posts.view` | stub | A | A | A | A | A |
| `postsResume` | `posts.bulk` | stub | A | A | A | D | D |
| `postsSchedule` | `posts.schedule` | stub | A | A | A | D | D |
| `projectsDelete` | `projects.manage` | yes | A | A | D | D | D |
| `projectsGet` | `projects.view` | yes | A | A | A | A | A |
| `projectsUpdate` | `projects.manage` | yes | A | A | D | D | D |
| `projectsSetMembers` | `members.update` | yes | A | A | D | D | D |
| `publicationsList` | `posts.view` | stub | A | A | A | A | A |
| `publicationsGet` | `posts.view` | stub | A | A | A | A | A |
| `publicationsRetry` | `posts.retry_publish` | stub | A | A | A | D | D |
| `workspacesDelete` | `workspace.delete` | yes | A | D | D | D | D |
| `workspacesGet` | `workspace.view` | yes | A | A | A | A | A |
| `workspacesUpdate` | `workspace.update` | yes | A | A | D | D | D |
| `workspacesListAudit` | `audit.view` | yes | A | A | D | D | D |
| `invitationsList` | `members.view` | yes | A | A | A | A | A |
| `invitationsCreate` | `members.invite` | yes | A | A | D | D | D |
| `workspaceMembersList` | `members.view` | yes | A | A | A | A | A |
| `workspaceMembersRemove` | `members.remove` | yes | A | A | D | D | D |
| `workspaceMembersUpdate` | `members.update` | yes | A | A | D | D | D |
| `projectsList` | `projects.view` | yes | A | A | A | A | A |
| `projectsCreate` | `projects.manage` | yes | A | A | D | D | D |

## Синхронизация

- `RbacOpenApiEndpointMapSyncTest` — падает, если в OpenAPI появился operationId без записи в карте.
- `RbacMatrixDocsSyncTest` — падает при рассинхроне product YAML и classpath-копии BE-6.
- `RbacMatrixFullCoverageTest` — все пары роль×action из YAML (unit).
- `RbacEndpointMatrixIT` / `RbacIdorMatrixIT` — HTTP; skip при 501.
- Сценарные TC-RBAC-01…05 / TC-IDOR-01 — `WorkspaceRbacIntegrationTest` (не дублируются).
