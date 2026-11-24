#!/usr/bin/env python3
"""Генератор docs/qa/rbac-endpoint-map.yaml (QA-6).

Читает docs/api/openapi.yaml и курируемый словарь operationId → RBAC action.
Каждый operationId обязан попасть в карту (иначе exit 1).
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
OPENAPI = ROOT / "docs" / "api" / "openapi.yaml"
OUT = ROOT / "docs" / "qa" / "rbac-endpoint-map.yaml"

# None = вне матрицы workspace RBAC (auth / me / public / create-own-ws)
ACTION: dict[str, str | None] = {
    "jwksGet": None,
    "ping": None,
    "auth2faConfirm": None,
    "auth2faDisable": None,
    "auth2faSetup": None,
    "authVerifyEmail": None,
    "authLogin": None,
    "authLogout": None,
    "authOauthLink": None,
    "authOauthCallback": None,
    "authOauthStart": None,
    "authPasswordForgot": None,
    "authPasswordReset": None,
    "authRefresh": None,
    "authRegister": None,
    "meDelete": None,
    "meGet": None,
    "meUpdate": None,
    "meChangePassword": None,
    "meRevokeOtherSessions": None,
    "meListSessions": None,
    "meRevokeSession": None,
    "invitationsAccept": None,
    "invitationsPreview": None,
    "workspacesList": None,
    "workspacesCreate": None,
    "networksCapabilities": None,
    "ingestModeMatrix": None,
    "hooksVk": None,
    "hooksTg": None,
    "hooksMax": None,
    "webhooksYookassa": None,
    "hooksTelegramNotify": None,
    "billingListPlans": None,
    "workspacesGet": "workspace.view",
    "workspacesUpdate": "workspace.update",
    "workspacesDelete": "workspace.delete",
    "workspacesListAudit": "audit.view",
    "workspaceMembersList": "members.view",
    "workspaceMembersUpdate": "members.update",
    "workspaceMembersRemove": "members.remove",
    "invitationsList": "members.view",
    "invitationsCreate": "members.invite",
    "invitationsCancel": "members.invite",
    "projectsList": "projects.view",
    "projectsCreate": "projects.manage",
    "projectsGet": "projects.view",
    "projectsUpdate": "projects.manage",
    "projectsDelete": "projects.manage",
    "projectsSetMembers": "members.update",
    "billingCheckout": "billing.manage",
    "billingListInvoices": "billing.view",
    "billingApplyPromo": "billing.apply_promo",
    "billingGetSubscription": "billing.view",
    "billingStartTrial": "billing.manage",
    "billingChangePlan": "billing.manage",
    "billingDeletePaymentMethod": "billing.manage",
    "billingGetAutopay": "billing.view",
    "billingGetPaymentMethod": "billing.view",
    "billingGetWallet": "billing.view",
    "billingInvoiceActPdf": "billing.view",
    "billingInvoicePdf": "billing.view",
    "billingListPayments": "billing.view",
    "billingListTransactions": "billing.view",
    "billingRequestInvoice": "billing.manage",
    "billingSetAutoRenew": "billing.manage",
    "billingUpdateAutopay": "billing.manage",
    "channelsList": "channels.view",
    "channelsGet": "channels.view",
    "channelsUpdate": "channels.manage",
    "channelsDelete": "channels.manage",
    "channelsCheck": "channels.manage",
    "channelsConnectStart": "channels.manage",
    "channelsConnectConfirm": "channels.manage",
    "channelsConnectCallback": "channels.manage",
    "channelsStoryConnectionGet": "channels.view",
    "channelsStoryConnectionStart": "channels.manage",
    "channelsEventSubscriptionGet": "automations.view",
    "channelsEventSubscriptionRepair": "automations.manage",
    "channelSetsList": "channel_sets.view",
    "channelSetsGet": "channel_sets.view",
    "channelSetsCreate": "channel_sets.manage",
    "channelSetsUpdate": "channel_sets.manage",
    "channelSetsDelete": "channel_sets.manage",
    "postsList": "posts.view",
    "postsGet": "posts.view",
    "postsCreate": "posts.create",
    "postsUpdate": "posts.update",
    "postsDelete": "posts.delete",
    "postsDuplicate": "posts.create",
    "postsSchedule": "posts.schedule",
    "postsPause": "posts.bulk",
    "postsResume": "posts.bulk",
    "postsBulk": "posts.bulk",
    "postsBatch": "posts.bulk",
    "postsPreview": "posts.view",
    "postsListPublications": "posts.view",
    "storiesBatch": "posts.bulk",
    "mediaList": "media.view",
    "mediaListFolders": "media.view",
    "mediaGetQuota": "media.view",
    "mediaGet": "media.view",
    "mediaCreateUpload": "media.manage",
    "mediaPresignPart": "media.manage",
    "mediaCompleteUpload": "media.manage",
    "mediaDelete": "media.manage",
    "mediaImportUrl": "media.manage",
    "calendarList": "calendar.view",
    "calendarMove": "calendar.move",
    "publicationsList": "posts.view",
    "publicationsGet": "posts.view",
    "publicationsRetry": "posts.retry_publish",
    "publicationsListAttempts": "posts.view",
    "notificationSettingsGet": "notifications.manage_self",
    "notificationSettingsUpdate": "notifications.manage_self",
    "notificationsList": "notifications.manage_self",
    "notificationsMarkRead": "notifications.manage_self",
    "notificationTelegramLinkStart": "notifications.manage_self",
    "notificationTelegramUnlink": "notifications.manage_self",
    "notificationWebPushSubscribe": "notifications.manage_self",
    "notificationWebPushUnsubscribe": "notifications.manage_self",
    "notificationVapidPublicKey": None,
    "notificationUnsubscribe": None,
    "ordConnectionsList": "ord.view",
    "ordConnectionsUpsert": "ord.connection.manage",
    "ordConnectionsTest": "ord.connection.manage",
    "ordContractorsList": "ord.view",
    "ordContractorsCreate": "ord.contractors.manage",
    "ordContractorsGet": "ord.view",
    "ordContractorsUpdate": "ord.contractors.manage",
    "ordContractsList": "ord.view",
    "ordContractsCreate": "ord.contractors.manage",
    "ordDictionariesKktu": None,
    "ordDictionariesOksm": None,
    "ordVatCalculate": None,
    "postsOrdRegister": "ord.register",
    "postsOrdStatus": "ord.view",
    "ordReportsRun": "ord.reports.run",
    "ordReportsList": "ord.view",
    "ordReportsExport": "ord.view",
    "ordCreativesList": "ord.view",
    "ordEridCheck": "ord.view",
    "ordInvoicesDelete": "ord.reports.run",
    "analyticsPostsGet": "analytics.view",
    "ingestRulesList": "automations.view",
    "ingestRulesCreate": "automations.manage",
    "ingestRulesGet": "automations.view",
    "ingestRulesPatch": "automations.manage",
    "ingestRulesActivate": "automations.manage",
    "ingestRulesPause": "automations.manage",
    "ingestRulesResume": "automations.manage",
    "ingestRulesArchive": "automations.manage",
    "ingestRulesRun": "automations.manage",
    "ingestRunsList": "automations.view",
    "ingestItemsList": "automations.view",
    "ingestItemsGet": "automations.view",
    "ingestItemsApprove": "automations.moderate",
    "ingestItemsReject": "automations.moderate",
    "ingestItemsEditAndApprove": "automations.moderate",
    "ingestItemsBulk": "automations.moderate",
    "ingestItemsExport": "automations.view",
    "ingestRulesCloneConfig": "automations.manage",
    "ingestPreview": "automations.view",
    # BE-35 antispam
    "antispamRulesList": "automations.view",
    "antispamRulesCreate": "automations.manage",
    "antispamRulesGet": "automations.view",
    "antispamRulesPatch": "automations.manage",
    "antispamRulesActivate": "automations.manage",
    "antispamRulesPause": "automations.manage",
    "antispamRulesResume": "automations.manage",
    "antispamRulesArchive": "automations.manage",
    "antispamRulesScan": "automations.moderate",
    "antispamRulesStats": "automations.view",
    "antispamWordListsList": "automations.view",
    "antispamWordListsCreate": "automations.manage",
    "antispamWordListsPatch": "automations.manage",
    "antispamWordListsDelete": "automations.manage",
    "antispamLogList": "automations.view",
    "antispamLogRestore": "automations.moderate",
    # BE-34 VK suggests
    "suggestSettingsList": "automations.view",
    "suggestSettingsCreate": "automations.manage",
    "suggestSettingsGet": "automations.view",
    "suggestSettingsPatch": "automations.manage",
    "suggestSettingsActivate": "automations.manage",
    "suggestSettingsPause": "automations.manage",
    "suggestSettingsResume": "automations.manage",
    "suggestSettingsCopy": "automations.manage",
    "suggestSettingsSync": "automations.manage",
    "suggestItemsList": "automations.view",
    "suggestItemsGet": "automations.view",
    "suggestItemsApprove": "automations.moderate",
    "suggestItemsReject": "automations.moderate",
    "suggestItemsBulk": "automations.moderate",
    "suggestBulkJobGet": "automations.moderate",
    # BE-36 crosspost
    "crosspostRulesList": "automations.view",
    "crosspostRulesCreate": "automations.manage",
    "crosspostRulesGet": "automations.view",
    "crosspostRulesPatch": "automations.manage",
    "crosspostRulesActivate": "automations.manage",
    "crosspostRulesPause": "automations.manage",
    "crosspostRulesPreview": "automations.view",
    "crosspostItemsList": "automations.view",
    "crosspostItemsRetry": "automations.moderate",
    # BE-13 platform admin (users.platform_admin) — вне workspace RBAC
    "adminUsersSearch": None,
    "adminUsersGet": None,
    "adminUsersImpersonate": None,
    "adminWorkspacesSearch": None,
    "adminWorkspacesGet": None,
    "adminWorkspacesAdjustSubscription": None,
    "adminWorkspacesAdjustBalance": None,
    "adminPlansList": None,
    "adminPlansUpsert": None,
    "adminPromosList": None,
    "adminPromosCreate": None,
    "adminPromosUpdate": None,
    "adminQueuesList": None,
    "adminQueuesListMessages": None,
    "adminQueuesRetryMessage": None,
    "adminQueuesDeleteMessage": None,
    "adminFeatureFlagsList": None,
    "adminFeatureFlagsUpsert": None,
    "adminWorkspaceFeatureFlagsSet": None,
    "adminAnnouncementsList": None,
    "adminAnnouncementsCreate": None,
    "adminAnnouncementsUpdate": None,
    "adminAuditList": None,
}

IMPLEMENTED = {
    "workspacesList",
    "workspacesCreate",
    "workspacesGet",
    "workspacesUpdate",
    "workspacesDelete",
    "workspacesListAudit",
    "workspaceMembersList",
    "workspaceMembersUpdate",
    "workspaceMembersRemove",
    "invitationsList",
    "invitationsCreate",
    "invitationsCancel",
    "invitationsAccept",
    "invitationsPreview",
    "projectsList",
    "projectsCreate",
    "projectsGet",
    "projectsUpdate",
    "projectsDelete",
    "projectsSetMembers",
    "jwksGet",
    "ping",
    "auth2faConfirm",
    "auth2faDisable",
    "auth2faSetup",
    "authVerifyEmail",
    "authLogin",
    "authLogout",
    "authOauthLink",
    "authOauthCallback",
    "authOauthStart",
    "authPasswordForgot",
    "authPasswordReset",
    "authRefresh",
    "authRegister",
    "meDelete",
    "meGet",
    "meUpdate",
    "meChangePassword",
    "meRevokeOtherSessions",
    "meListSessions",
    "meRevokeSession",
    "mediaList",
    "mediaListFolders",
    "mediaGetQuota",
    "mediaGet",
    "mediaCreateUpload",
    "mediaPresignPart",
    "mediaCompleteUpload",
    "mediaDelete",
    "mediaImportUrl",
    "channelsList",
    "channelsGet",
    "channelsUpdate",
    "channelsDelete",
    "channelsCheck",
    "channelsConnectStart",
    "channelsConnectConfirm",
    "channelsConnectCallback",
    "channelsStoryConnectionGet",
    "channelsStoryConnectionStart",
    "channelsEventSubscriptionGet",
    "channelsEventSubscriptionRepair",
    "channelSetsList",
    "channelSetsGet",
    "channelSetsCreate",
    "channelSetsUpdate",
    "channelSetsDelete",
    "networksCapabilities",
    "ingestModeMatrix",
    "hooksVk",
    "hooksTg",
    "hooksMax",
    "webhooksYookassa",
    "hooksTelegramNotify",
    "billingListPlans",
    "billingGetSubscription",
    "billingCheckout",
    "billingListInvoices",
    "billingApplyPromo",
    "billingStartTrial",
    "notificationSettingsGet",
    "notificationSettingsUpdate",
    "notificationsList",
    "notificationsMarkRead",
    "notificationTelegramLinkStart",
    "notificationTelegramUnlink",
    "notificationWebPushSubscribe",
    "notificationWebPushUnsubscribe",
    "notificationVapidPublicKey",
    "notificationUnsubscribe",
    "postsList",
    "postsGet",
    "postsCreate",
    "postsUpdate",
    "postsDelete",
    "postsDuplicate",
    "postsSchedule",
    "postsPause",
    "postsResume",
    "postsBulk",
    "postsBatch",
    "postsPreview",
    "postsListPublications",
    "storiesBatch",
    "calendarList",
    "calendarMove",
    "publicationsList",
    "publicationsGet",
    "publicationsRetry",
    "publicationsListAttempts",
    "ordConnectionsList",
    "ordConnectionsUpsert",
    "ordConnectionsTest",
    "ordContractorsList",
    "ordContractorsCreate",
    "ordContractorsGet",
    "ordContractorsUpdate",
    "ordContractsList",
    "ordContractsCreate",
    "ordDictionariesKktu",
    "ordDictionariesOksm",
    "ordVatCalculate",
    "postsOrdRegister",
    "postsOrdStatus",
    "ordReportsRun",
    "ordReportsList",
    "ordReportsExport",
    "ordCreativesList",
    "ordEridCheck",
    "ordInvoicesDelete",
    "ingestRulesList",
    "ingestRulesCreate",
    "ingestRulesGet",
    "ingestRulesPatch",
    "ingestRulesActivate",
    "ingestRulesPause",
    "ingestRulesResume",
    "ingestRulesArchive",
    "ingestRulesRun",
    "ingestRunsList",
    "ingestItemsList",
    "ingestItemsGet",
    "ingestItemsApprove",
    "ingestItemsReject",
    "ingestItemsEditAndApprove",
    "ingestItemsBulk",
    "ingestItemsExport",
    "ingestRulesCloneConfig",
    "ingestPreview",
    "antispamRulesList",
    "antispamRulesCreate",
    "antispamRulesGet",
    "antispamRulesPatch",
    "antispamRulesActivate",
    "antispamRulesPause",
    "antispamRulesResume",
    "antispamRulesArchive",
    "antispamRulesScan",
    "antispamRulesStats",
    "antispamWordListsList",
    "antispamWordListsCreate",
    "antispamWordListsPatch",
    "antispamWordListsDelete",
    "antispamLogList",
    "antispamLogRestore",
    "suggestSettingsList",
    "suggestSettingsCreate",
    "suggestSettingsGet",
    "suggestSettingsPatch",
    "suggestSettingsActivate",
    "suggestSettingsPause",
    "suggestSettingsResume",
    "suggestSettingsCopy",
    "suggestSettingsSync",
    "suggestItemsList",
    "suggestItemsGet",
    "suggestItemsApprove",
    "suggestItemsReject",
    "suggestItemsBulk",
    "suggestBulkJobGet",
    "crosspostRulesList",
    "crosspostRulesCreate",
    "crosspostRulesGet",
    "crosspostRulesPatch",
    "crosspostRulesActivate",
    "crosspostRulesPause",
    "crosspostRulesPreview",
    "crosspostItemsList",
    "crosspostItemsRetry",
    "billingChangePlan",
    "billingDeletePaymentMethod",
    "billingGetAutopay",
    "billingGetPaymentMethod",
    "billingGetWallet",
    "billingInvoiceActPdf",
    "billingInvoicePdf",
    "billingListPayments",
    "billingListTransactions",
    "billingRequestInvoice",
    "billingSetAutoRenew",
    "billingUpdateAutopay",
    "postsBatch",
    "adminUsersSearch",
    "adminUsersGet",
    "adminUsersImpersonate",
    "adminWorkspacesSearch",
    "adminWorkspacesGet",
    "adminWorkspacesAdjustSubscription",
    "adminWorkspacesAdjustBalance",
    "adminPlansList",
    "adminPlansUpsert",
    "adminPromosList",
    "adminPromosCreate",
    "adminPromosUpdate",
    "adminQueuesList",
    "adminQueuesListMessages",
    "adminQueuesRetryMessage",
    "adminQueuesDeleteMessage",
    "adminFeatureFlagsList",
    "adminFeatureFlagsUpsert",
    "adminWorkspaceFeatureFlagsSet",
    "adminAnnouncementsList",
    "adminAnnouncementsCreate",
    "adminAnnouncementsUpdate",
    "adminAuditList",
}

DESTRUCTIVE = {
    "workspacesDelete",
    "projectsDelete",
    "workspaceMembersRemove",
    "invitationsCancel",
    "channelsDelete",
    "channelSetsDelete",
    "postsDelete",
    "meDelete",
    "mediaDelete",
    "ordInvoicesDelete",
    "billingDeletePaymentMethod",
    "antispamWordListsDelete",
}

WORKSPACE_QUERY_OPS = {
    "channelsList",
    "channelsConnectStart",
    "channelSetsList",
    "postsList",
    "mediaList",
    "calendarList",
    "publicationsList",
    "billingCheckout",
    "billingListInvoices",
    "billingApplyPromo",
    "billingGetSubscription",
    "billingStartTrial",
    "billingGetAutopay",
    "billingUpdateAutopay",
    "billingGetPaymentMethod",
    "billingDeletePaymentMethod",
    "billingGetWallet",
    "billingListPayments",
    "billingListTransactions",
    "billingRequestInvoice",
    "billingSetAutoRenew",
    "billingChangePlan",
    "notificationSettingsGet",
    "notificationSettingsUpdate",
    "notificationsList",
    "notificationsMarkRead",
    "notificationTelegramLinkStart",
    "notificationTelegramUnlink",
    "notificationWebPushSubscribe",
    "notificationWebPushUnsubscribe",
    # postsPreview / postsBulk / postsBatch: no workspaceId query — tenant via body/resource id
    # notificationVapidPublicKey / notificationUnsubscribe: no workspace query
    "mediaCreateUpload",
    "mediaImportUrl",
    "mediaListFolders",
    "mediaGetQuota",
    "channelSetsCreate",
    "ordReportsRun",
    "ordCreativesList",
    "ingestPreview",
    "storiesBatch",
}

# Extra query params beyond workspaceId (probe fixtures / required OpenAPI params).
EXTRA_QUERY = {
    "ingestItemsExport": {"format": "csv"},
}


def parse_operations(text: str) -> list[dict[str, str]]:
    ops: list[dict[str, str]] = []
    current_path: str | None = None
    current_method: str | None = None
    for line in text.splitlines():
        m = re.match(r"^  (/\S+):", line)
        if m:
            current_path = m.group(1)
            current_method = None
            continue
        m = re.match(r"^    (get|post|put|patch|delete):", line)
        if m and current_path:
            current_method = m.group(1).upper()
            continue
        m = re.match(r"^      operationId:\s*(\S+)", line)
        if m and current_path and current_method:
            ops.append(
                {
                    "operationId": m.group(1),
                    "method": current_method,
                    "path": current_path,
                }
            )
            current_method = None
    return ops


def placeholders(path: str) -> dict[str, str]:
    ph: dict[str, str] = {}
    mapping = {
        "{workspaceId}": ("workspaceId", "{workspaceId}"),
        "{projectId}": ("projectId", "{projectId}"),
        "{userId}": ("userId", "{memberUserId}"),
        "{invitationId}": ("invitationId", "{invitationId}"),
        "{channelId}": ("channelId", "{channelId}"),
        "{botId}": ("botId", "{botId}"),
        "{setId}": ("setId", "{channelSetId}"),
        "{postId}": ("postId", "{postId}"),
        "{mediaId}": ("mediaId", "{mediaId}"),
        "{partNumber}": ("partNumber", "1"),
        "{publicationId}": ("publicationId", "{publicationId}"),
        "{sessionId}": ("sessionId", "{sessionId}"),
        "{ruleId}": ("ruleId", "{ruleId}"),
        "{itemId}": ("itemId", "{itemId}"),
        "{settingsId}": ("settingsId", "{settingsId}"),
        "{jobId}": ("jobId", "{jobId}"),
        "{listId}": ("listId", "{antispamListId}"),
        "{logId}": ("logId", "{antispamLogId}"),
        "{network}": ("network", "vk"),
        "{provider}": ("provider", "vk_ord"),
        "{contractorId}": ("contractorId", "{contractorId}"),
        "{invoiceId}": ("invoiceId", "{invoiceId}"),
        "{contractId}": ("contractId", "{contractId}"),
        # Billing invoice PDF/act paths use generic {id}; probe resolves via {invoiceId}.
        "{id}": ("id", "{invoiceId}"),
    }
    for token, (key, value) in mapping.items():
        if token in path:
            ph[key] = value
    # Antispam rule IDs must not collide with grabber/crosspost {ruleId} fixtures.
    if "/antispam/" in path and "ruleId" in ph:
        ph["ruleId"] = "{antispamRuleId}"
    return ph


def main() -> int:
    ops = parse_operations(OPENAPI.read_text(encoding="utf-8"))
    op_ids = {o["operationId"] for o in ops}
    missing = sorted(op_ids - set(ACTION))
    extra = sorted(set(ACTION) - op_ids)
    if missing or extra:
        print("ACTION map out of sync with OpenAPI:", file=sys.stderr)
        if missing:
            print("  missing in ACTION:", ", ".join(missing), file=sys.stderr)
        if extra:
            print("  extra in ACTION:", ", ".join(extra), file=sys.stderr)
        return 1

    endpoints: list[dict] = []
    for o in ops:
        oid = o["operationId"]
        action = ACTION[oid]
        entry: dict = {
            "operationId": oid,
            "method": o["method"],
            "path": o["path"],
            "implementedHint": oid in IMPLEMENTED,
        }
        if action is None:
            entry["rbac"] = "none"
        else:
            entry["rbac"] = "matrix"
            entry["action"] = action
            ph = placeholders(o["path"])
            if ph:
                entry["pathParams"] = ph
            if oid in WORKSPACE_QUERY_OPS:
                entry["query"] = {"workspaceId": "{workspaceId}"}
            if oid in EXTRA_QUERY:
                q = dict(entry.get("query") or {})
                q.update(EXTRA_QUERY[oid])
                entry["query"] = q
            if oid in DESTRUCTIVE:
                entry["destructive"] = True
        endpoints.append(entry)

    doc = {
        "version": "1.0.0",
        "updated": "2026-11-17",
        "task": "QA-6",
        "schema": "posts.rbac.endpoint-map.v1",
        "description": (
            "Карта OpenAPI operationId → RBAC action. Каждый operationId из "
            "docs/api/openapi.yaml обязан иметь запись. rbac=none — вне матрицы "
            "ролей workspace. HTTP-тесты TC-RBAC пропускают эндпоинт при 501 и "
            "включают автоматически, когда ответ не 501. Рассинхрон OpenAPI↔карта "
            "ловится тестом."
        ),
        "sources": {
            "openapi": "docs/api/openapi.yaml",
            "roles_permissions": "docs/product/roles-permissions.yaml",
        },
        "test_contract": {
            "allow_http": "не 401/403 (2xx или бизнес-4xx); 501 → skip",
            "deny_same_workspace_http": 403,
            "foreign_workspace_http": 404,
        },
        "endpoints": endpoints,
    }

    header = (
        "# Карта RBAC эндпоинтов (QA-6). Генерируется: python docs/qa/generate_rbac_endpoint_map.py\n"
        "# Источник правды по правам: docs/product/roles-permissions.yaml\n"
        "# Не коммитьте карту, рассинхронизированную с OpenAPI (CI упадёт).\n"
    )
    OUT.write_text(
        header + yaml.dump(doc, allow_unicode=True, sort_keys=False, default_flow_style=False, width=100),
        encoding="utf-8",
    )
    matrix = sum(1 for e in endpoints if e.get("rbac") == "matrix")
    print(f"Wrote {OUT.relative_to(ROOT)}: {len(endpoints)} ops, {matrix} matrix-bound")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
