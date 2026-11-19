import type {
  AntispamLogEntry,
  AntispamLogPage,
  AntispamRestoreRequest,
  AntispamRule,
  AntispamRuleCreate,
  AntispamRulePage,
  AntispamRulePatch,
  AntispamRuleStats,
  AntispamScanResponse,
  AntispamWordList,
  AntispamWordListCreate,
  AntispamWordListPage,
  AntispamWordListPatch,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export async function listAntispamRules(params: {
  workspaceId: string;
  projectId: string;
  cursor?: string;
  limit?: number;
}): Promise<AntispamRulePage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/projects/{projectId}/antispam/rules', {
      params: {
        path: { workspaceId: params.workspaceId, projectId: params.projectId },
        query: {
          cursor: params.cursor,
          limit: params.limit ?? 50,
        },
      },
    }),
  );
}

export async function createAntispamRule(
  workspaceId: string,
  projectId: string,
  body: AntispamRuleCreate,
): Promise<AntispamRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/workspaces/{workspaceId}/projects/{projectId}/antispam/rules', {
      params: {
        path: { workspaceId, projectId },
      },
      body,
    }),
  );
}

export async function getAntispamRule(ruleId: string): Promise<AntispamRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/antispam/rules/{ruleId}', {
      params: { path: { ruleId } },
    }),
  );
}

export async function patchAntispamRule(
  ruleId: string,
  body: AntispamRulePatch,
): Promise<AntispamRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.PATCH('/api/v1/antispam/rules/{ruleId}', {
      params: { path: { ruleId } },
      body,
    }),
  );
}

export async function activateAntispamRule(ruleId: string): Promise<AntispamRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/antispam/rules/{ruleId}/activate', {
      params: { path: { ruleId } },
    }),
  );
}

export async function pauseAntispamRule(ruleId: string): Promise<AntispamRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/antispam/rules/{ruleId}/pause', {
      params: { path: { ruleId } },
    }),
  );
}

export async function resumeAntispamRule(ruleId: string): Promise<AntispamRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/antispam/rules/{ruleId}/resume', {
      params: { path: { ruleId } },
    }),
  );
}

export async function archiveAntispamRule(ruleId: string): Promise<AntispamRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/antispam/rules/{ruleId}/archive', {
      params: { path: { ruleId } },
    }),
  );
}

export async function getAntispamRuleStats(ruleId: string): Promise<AntispamRuleStats> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/antispam/rules/{ruleId}/stats', {
      params: { path: { ruleId } },
    }),
  );
}

export async function scanAntispamRule(ruleId: string): Promise<AntispamScanResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/antispam/rules/{ruleId}/scan', {
      params: { path: { ruleId } },
    }),
  );
}

export async function listAntispamLog(params: {
  workspaceId: string;
  channelId?: string;
  section?: string;
  ruleId?: string;
  action?: string;
  from?: string;
  to?: string;
  cursor?: string;
  limit?: number;
}): Promise<AntispamLogPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/antispam/log', {
      params: {
        path: { workspaceId: params.workspaceId },
        query: {
          channelId: params.channelId,
          section: params.section,
          ruleId: params.ruleId,
          action: params.action,
          from: params.from,
          to: params.to,
          cursor: params.cursor,
          limit: params.limit ?? 50,
        },
      },
    }),
  );
}

export async function restoreAntispamLog(
  logId: string,
  body: AntispamRestoreRequest = {},
): Promise<AntispamLogEntry> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/antispam/log/{logId}/restore', {
      params: { path: { logId } },
      body,
    }),
  );
}

export async function listAntispamWordLists(params: {
  workspaceId: string;
  cursor?: string;
  limit?: number;
}): Promise<AntispamWordListPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/antispam/word-lists', {
      params: {
        path: { workspaceId: params.workspaceId },
        query: {
          cursor: params.cursor,
          limit: params.limit ?? 50,
        },
      },
    }),
  );
}

export async function createAntispamWordList(
  workspaceId: string,
  body: AntispamWordListCreate,
): Promise<AntispamWordList> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/workspaces/{workspaceId}/antispam/word-lists', {
      params: {
        path: { workspaceId },
      },
      body,
    }),
  );
}

export async function patchAntispamWordList(
  listId: string,
  body: AntispamWordListPatch,
): Promise<AntispamWordList> {
  const client = getApiClient();
  return unwrapApi(
    await client.PATCH('/api/v1/antispam/word-lists/{listId}', {
      params: { path: { listId } },
      body,
    }),
  );
}

export async function deleteAntispamWordList(listId: string): Promise<void> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/antispam/word-lists/{listId}', {
    params: { path: { listId } },
  });
  if (!result.response.ok) {
    unwrapApi(result);
  }
}
