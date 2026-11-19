import type {
  CrosspostItem,
  CrosspostItemPage,
  CrosspostPreviewRequest,
  CrosspostPreviewResponse,
  CrosspostRule,
  CrosspostRuleCreate,
  CrosspostRuleList,
  CrosspostRulePatch,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

import type { ItemStatus } from '../model/types';

function idempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export async function listCrosspostRules(params: {
  workspaceId: string;
  projectId: string;
}): Promise<CrosspostRuleList> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/projects/{projectId}/crosspost/rules', {
      params: {
        path: { workspaceId: params.workspaceId, projectId: params.projectId },
      },
    }),
  );
}

export async function createCrosspostRule(
  workspaceId: string,
  projectId: string,
  body: CrosspostRuleCreate,
): Promise<CrosspostRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/workspaces/{workspaceId}/projects/{projectId}/crosspost/rules', {
      params: {
        path: { workspaceId, projectId },
        header: { 'Idempotency-Key': idempotencyKey() },
      },
      body,
    }),
  );
}

export async function getCrosspostRule(ruleId: string): Promise<CrosspostRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/crosspost/rules/{ruleId}', {
      params: { path: { ruleId } },
    }),
  );
}

export async function patchCrosspostRule(
  ruleId: string,
  body: CrosspostRulePatch,
): Promise<CrosspostRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.PATCH('/api/v1/crosspost/rules/{ruleId}', {
      params: { path: { ruleId } },
      body,
    }),
  );
}

export async function activateCrosspostRule(ruleId: string): Promise<CrosspostRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/crosspost/rules/{ruleId}/activate', {
      params: { path: { ruleId } },
    }),
  );
}

export async function pauseCrosspostRule(ruleId: string): Promise<CrosspostRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/crosspost/rules/{ruleId}/pause', {
      params: { path: { ruleId } },
    }),
  );
}

export async function listCrosspostItems(params: {
  ruleId: string;
  status?: ItemStatus | string;
  cursor?: string;
  limit?: number;
}): Promise<CrosspostItemPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/crosspost/rules/{ruleId}/items', {
      params: {
        path: { ruleId: params.ruleId },
        query: {
          status: params.status,
          cursor: params.cursor,
          limit: params.limit ?? 50,
        },
      },
    }),
  );
}

export async function previewCrosspostRule(
  ruleId: string,
  body: CrosspostPreviewRequest,
): Promise<CrosspostPreviewResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/crosspost/rules/{ruleId}/preview', {
      params: { path: { ruleId } },
      body,
    }),
  );
}

export async function retryCrosspostItem(itemId: string): Promise<CrosspostItem> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/crosspost/items/{itemId}/retry', {
      params: { path: { itemId } },
    }),
  );
}
