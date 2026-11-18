import type {
  CreateOrdContractorRequest,
  OrdConnection,
  OrdConnectionPage,
  OrdConnectionTestResponse,
  OrdContractPage,
  OrdContractor,
  OrdContractorPage,
  OrdProvider,
  UpdateOrdContractorRequest,
  UpsertOrdConnectionRequest,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

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

export async function listOrdKktu(): Promise<OrdDictionaryItem[]> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/ord/dictionaries/kktu', {}));
}

export async function listOrdOksm(): Promise<OrdDictionaryItem[]> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/ord/dictionaries/oksm', {}));
}