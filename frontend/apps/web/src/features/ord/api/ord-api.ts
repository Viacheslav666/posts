import type {
  CreateOrdContractorRequest,
  OrdConnection,
  OrdConnectionPage,
  OrdConnectionTestResponse,
  OrdContractPage,
  OrdContractor,
  OrdContractorPage,
  OrdEridCheckRequest,
  OrdEridCheckResponse,
  OrdProvider,
  OrdReportRowPage,
  OrdReportRunRequest,
  OrdReportRunResponse,
  OrdVatCalculateRequest,
  UpdateOrdContractorRequest,
  UpsertOrdConnectionRequest,
  VatBreakdown,
} from '@posts/api-client';

import { getAccessToken, getApiBaseUrl, getApiClient, unwrapApi } from '@/shared/api-client';
import { toApiProblemError } from '@/shared/lib/errors';

import type { OrdExportFormat } from '../model/report-export';
import type { OrdReportApiQuery } from '../model/report-filters';

export type OrdDictionaryItem = { code?: string; name?: string };

export async function listOrdConnections(workspaceId: string): Promise<OrdConnectionPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/ord/connections', {
      params: { path: { workspaceId } },
    }),
  );
}

export async function upsertOrdConnection(
  workspaceId: string,
  provider: OrdProvider,
  body: UpsertOrdConnectionRequest,
): Promise<OrdConnection> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/workspaces/{workspaceId}/ord/connections/{provider}', {
      params: { path: { workspaceId, provider } },
      body,
    }),
  );
}

export async function testOrdConnection(
  workspaceId: string,
  provider: OrdProvider,
): Promise<OrdConnectionTestResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/workspaces/{workspaceId}/ord/connections/{provider}/test', {
      params: { path: { workspaceId, provider } },
    }),
  );
}

export async function listOrdContractors(workspaceId: string): Promise<OrdContractorPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/ord/contractors', {
      params: { path: { workspaceId } },
    }),
  );
}

export async function createOrdContractor(
  workspaceId: string,
  body: CreateOrdContractorRequest,
): Promise<OrdContractor> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/workspaces/{workspaceId}/ord/contractors', {
      params: { path: { workspaceId } },
      body,
    }),
  );
}

export async function updateOrdContractor(
  workspaceId: string,
  contractorId: string,
  body: UpdateOrdContractorRequest,
): Promise<OrdContractor> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/workspaces/{workspaceId}/ord/contractors/{contractorId}', {
      params: { path: { workspaceId, contractorId } },
      body,
    }),
  );
}

export async function listOrdContracts(workspaceId: string): Promise<OrdContractPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/ord/contracts', {
      params: { path: { workspaceId } },
    }),
  );
}

export async function listOrdKktu(q?: string): Promise<OrdDictionaryItem[]> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/ord/dictionaries/kktu', {
      params: { query: q?.trim() ? { q: q.trim() } : undefined },
    }),
  );
}

export async function listOrdOksm(q?: string): Promise<OrdDictionaryItem[]> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/ord/dictionaries/oksm', {
      params: { query: q?.trim() ? { q: q.trim() } : undefined },
    }),
  );
}

export async function calculateOrdVat(body: OrdVatCalculateRequest): Promise<VatBreakdown> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/ord/vat/calculate', {
      body,
    }),
  );
}

export async function listOrdReports(
  workspaceId: string,
  filters?: OrdReportApiQuery,
): Promise<OrdReportRowPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/ord/reports', {
      params: {
        path: { workspaceId },
        query: {
          year: filters?.year,
          month: filters?.month,
          provider: filters?.provider,
          status: filters?.status,
          erid: filters?.erid,
        },
      },
    }),
  );
}

export async function runOrdReport(body: OrdReportRunRequest): Promise<OrdReportRunResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/ord/reports/run', { body }));
}

export async function checkOrdErid(body: OrdEridCheckRequest): Promise<OrdEridCheckResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/ord/erid/check', { body }));
}

export async function deleteOrdInvoice(invoiceId: string): Promise<void> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/ord/invoices/{invoiceId}', {
    params: { path: { invoiceId } },
  });
  if (!result.response.ok) {
    unwrapApi(result);
  }
}

export type OrdReportsExportResult = {
  blob: Blob;
  filename: string;
  contentType: string;
};

/**
 * Binary CSV/XLSX export. Uses fetch+blob because OpenAPI marks the body as JSON byte string.
 */
export async function exportOrdReports(
  workspaceId: string,
  options: OrdReportApiQuery & { format?: OrdExportFormat },
): Promise<OrdReportsExportResult> {
  const format: OrdExportFormat = options.format ?? 'csv';
  const url = new URL(`${getApiBaseUrl()}/api/v1/workspaces/${workspaceId}/ord/reports/export`);
  url.searchParams.set('format', format);
  if (options.year != null) url.searchParams.set('year', String(options.year));
  if (options.month != null) url.searchParams.set('month', String(options.month));
  if (options.provider) url.searchParams.set('provider', options.provider);
  if (options.status) url.searchParams.set('status', options.status);

  const headers = new Headers({
    Accept:
      format === 'xlsx'
        ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        : 'text/csv',
  });
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
  const filename = match?.[1] ?? `ord-reports.${format === 'xlsx' ? 'xlsx' : 'csv'}`;
  const contentType =
    response.headers.get('Content-Type') ??
    (format === 'xlsx'
      ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      : 'text/csv; charset=utf-8');
  const blob = await response.blob();
  return { blob, filename, contentType };
}