import type {
  GrabberBulkModerationRequest,
  GrabberBulkModerationResponse,
  GrabberCloneConfigRequest,
  GrabberItem,
  GrabberItemApproveRequest,
  GrabberItemEditApproveRequest,
  GrabberItemPage,
  GrabberItemRejectRequest,
  GrabberRule,
  GrabberRuleCreate,
  GrabberRulePage,
  GrabberRulePatch,
  GrabberRun,
  GrabberRunPage,
  IngestPreviewRequest,
  IngestPreviewResponse,
} from '@posts/api-client';

import { getApiBaseUrl, getAccessToken, getApiClient, unwrapApi } from '@/shared/api-client';
import { toApiProblemError } from '@/shared/lib/errors';

import type { IngestRuleType, ItemStatus } from '../model/types';

function idempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export async function listIngestRules(params: {
  workspaceId: string;
  projectId: string;
  type?: IngestRuleType;
  cursor?: string;
  limit?: number;
}): Promise<GrabberRulePage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/projects/{projectId}/ingest/rules', {
      params: {
        path: { workspaceId: params.workspaceId, projectId: params.projectId },
        query: {
          type: params.type,
          cursor: params.cursor,
          limit: params.limit ?? 50,
        },
      },
    }),
  );
}

export async function createIngestRule(
  workspaceId: string,
  projectId: string,
  body: GrabberRuleCreate,
): Promise<GrabberRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/workspaces/{workspaceId}/projects/{projectId}/ingest/rules', {
      params: {
        path: { workspaceId, projectId },
        header: { 'Idempotency-Key': idempotencyKey() },
      },
      body,
    }),
  );
}

export async function getIngestRule(ruleId: string): Promise<GrabberRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/ingest/rules/{ruleId}', {
      params: { path: { ruleId } },
    }),
  );
}

export async function patchIngestRule(ruleId: string, body: GrabberRulePatch): Promise<GrabberRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.PATCH('/api/v1/ingest/rules/{ruleId}', {
      params: { path: { ruleId } },
      body,
    }),
  );
}

export async function activateIngestRule(ruleId: string): Promise<GrabberRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/rules/{ruleId}/activate', {
      params: { path: { ruleId } },
    }),
  );
}

export async function pauseIngestRule(ruleId: string): Promise<GrabberRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/rules/{ruleId}/pause', {
      params: { path: { ruleId } },
    }),
  );
}

export async function resumeIngestRule(ruleId: string): Promise<GrabberRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/rules/{ruleId}/resume', {
      params: { path: { ruleId } },
    }),
  );
}

export async function archiveIngestRule(ruleId: string): Promise<GrabberRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/rules/{ruleId}/archive', {
      params: { path: { ruleId } },
    }),
  );
}

export async function startIngestRun(ruleId: string): Promise<GrabberRun> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/rules/{ruleId}/runs', {
      params: {
        path: { ruleId },
        header: { 'Idempotency-Key': idempotencyKey() },
      },
    }),
  );
}

export async function listIngestRuns(
  ruleId: string,
  cursor?: string,
  limit = 20,
): Promise<GrabberRunPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/ingest/rules/{ruleId}/runs', {
      params: { path: { ruleId }, query: { cursor, limit } },
    }),
  );
}

export async function listIngestItems(params: {
  ruleId: string;
  status?: ItemStatus;
  cursor?: string;
  limit?: number;
}): Promise<GrabberItemPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/ingest/rules/{ruleId}/items', {
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

export async function getIngestItem(itemId: string): Promise<GrabberItem> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/ingest/items/{itemId}', {
      params: { path: { itemId } },
    }),
  );
}

export async function approveIngestItem(
  itemId: string,
  body?: GrabberItemApproveRequest,
): Promise<GrabberItem> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/items/{itemId}/approve', {
      params: {
        path: { itemId },
        header: { 'Idempotency-Key': idempotencyKey() },
      },
      body,
    }),
  );
}

export async function rejectIngestItem(
  itemId: string,
  body?: GrabberItemRejectRequest,
): Promise<GrabberItem> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/items/{itemId}/reject', {
      params: { path: { itemId } },
      body,
    }),
  );
}

export async function editAndApproveIngestItem(
  itemId: string,
  body: GrabberItemEditApproveRequest,
): Promise<GrabberItem> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/items/{itemId}/edit-and-approve', {
      params: {
        path: { itemId },
        header: { 'Idempotency-Key': idempotencyKey() },
      },
      body,
    }),
  );
}

export async function bulkModerateIngestItems(
  body: GrabberBulkModerationRequest,
): Promise<GrabberBulkModerationResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/items/bulk', {
      body,
    }),
  );
}

export async function previewIngestSource(
  workspaceId: string,
  body: IngestPreviewRequest,
): Promise<IngestPreviewResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/preview', {
      params: { query: { workspaceId } },
      body,
    }),
  );
}

export async function cloneIngestRuleConfig(
  ruleId: string,
  body: GrabberCloneConfigRequest,
): Promise<GrabberRule> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ingest/rules/{ruleId}/clone-config', {
      params: { path: { ruleId } },
      body,
    }),
  );
}

export type IngestExportResult = {
  blob: Blob;
  filename: string;
  contentType: string;
};

/**
 * Binary CSV/XLSX export. Uses fetch+blob because OpenAPI marks the body as JSON string.
 */
export async function exportIngestItems(
  ruleId: string,
  options?: { format?: 'csv' | 'xlsx'; status?: ItemStatus },
): Promise<IngestExportResult> {
  const format = options?.format ?? 'csv';
  const url = new URL(`${getApiBaseUrl()}/api/v1/ingest/rules/${ruleId}/items/export`);
  url.searchParams.set('format', format);
  if (options?.status) {
    url.searchParams.set('status', options.status);
  }

  const headers = new Headers({ Accept: format === 'xlsx' ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : 'text/csv' });
  const token = getAccessToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(url.toString(), {
    method: 'GET',
    headers,
    credentials: 'include',
  });

  if (!response.ok) {
    let problem: unknown = { title: response.statusText };
    try {
      problem = await response.json();
    } catch {
      // ignore non-JSON error bodies
    }
    throw toApiProblemError(problem, response.status);
  }

  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = disposition.match(/filename="?([^"]+)"?/i);
  const filename =
    match?.[1] ?? `grabber-${ruleId}.${format === 'xlsx' ? 'xlsx' : 'csv'}`;
  const contentType =
    response.headers.get('Content-Type') ??
    (format === 'xlsx'
      ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      : 'text/csv; charset=utf-8');
  const blob = await response.blob();
  return { blob, filename, contentType };
}
