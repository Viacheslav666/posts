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
    "hooksVk": None,
    "hooksTg": None,
    "hooksMax": None,
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
    "channelsList": "channels.view",
    "channelsGet": "channels.view",
    "channelsUpdate": "channels.manage",
    "channelsDelete": "channels.manage",
    "channelsCheck": "channels.manage",
    "channelsConnectStart": "channels.manage",
    "channelsConnectConfirm": "channels.manage",
    "channelsConnectCallback": "channels.manage",
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
    "postsPreview": "posts.view",
    "postsListPublications": "posts.view",
    "mediaList": "media.view",
    "mediaGet": "media.view",
    "mediaCreateUpload": "media.manage",
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
    "postsOrdRegister": "ord.view",
    "postsOrdStatus": "ord.view",
    "ordReportsRun": "ord.reports.run",
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
    "mediaGet",
    "mediaCreateUpload",
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
    "channelSetsList",
    "channelSetsGet",
    "channelSetsCreate",
    "channelSetsUpdate",
    "channelSetsDelete",
    "networksCapabilities",
    "hooksVk",
    "hooksTg",
    "hooksMax",
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
    "postsPreview",
    "postsListPublications",
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
    "postsOrdRegister",
    "postsOrdStatus",
    "ordReportsRun",
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
    "notificationSettingsGet",
    "notificationSettingsUpdate",
    "notificationsList",
    "notificationsMarkRead",
    # postsPreview / postsBulk: no workspaceId query — tenant via body/resource id
    "mediaCreateUpload",
    "mediaImportUrl",
    "channelSetsCreate",
    "ordReportsRun",
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
        "{publicationId}": ("publicationId", "{publicationId}"),
        "{sessionId}": ("sessionId", "{sessionId}"),
        "{network}": ("network", "vk"),
        "{provider}": ("provider", "vk_ord"),
        "{contractorId}": ("contractorId", "{contractorId}"),
    }
    for token, (key, value) in mapping.items():
        if token in path:
            ph[key] = value
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
