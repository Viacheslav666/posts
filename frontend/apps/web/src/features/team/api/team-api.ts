import type {
  AcceptInvitationRequest,
  AuditLogPage,
  CreateInvitationRequest,
  Invitation,
  InvitationPage,
  UpdateMemberRequest,
  Workspace,
  WorkspaceMember,
  WorkspaceMemberPage,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export async function listMembers(workspaceId: string): Promise<WorkspaceMemberPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/members', {
      params: { path: { workspaceId } },
    }),
  );
}

export async function updateMember(
  workspaceId: string,
  userId: string,
  body: UpdateMemberRequest,
): Promise<WorkspaceMember> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/workspaces/{workspaceId}/members/{userId}', {
      params: { path: { workspaceId, userId } },
      body,
    }),
  );
}

export async function removeMember(workspaceId: string, userId: string): Promise<void> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/workspaces/{workspaceId}/members/{userId}', {
    params: { path: { workspaceId, userId } },
  });
  if (!result.response.ok) {
    unwrapApi(result);
  }
}

export async function listInvitations(workspaceId: string): Promise<InvitationPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/invitations', {
      params: { path: { workspaceId } },
    }),
  );
}

export async function createInvitation(
  workspaceId: string,
  body: CreateInvitationRequest,
): Promise<Invitation> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/workspaces/{workspaceId}/invitations', {
      params: { path: { workspaceId } },
      body,
    }),
  );
}

export async function cancelInvitation(invitationId: string): Promise<void> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/invitations/{invitationId}', {
    params: { path: { invitationId } },
  });
  if (!result.response.ok) {
    unwrapApi(result);
  }
}

export async function previewInvitation(token: string): Promise<Invitation> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/invitations/preview', {
      params: { query: { token } },
    }),
  );
}

export async function acceptInvitation(body: AcceptInvitationRequest): Promise<Workspace> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/invitations/accept', { body }));
}

export async function listAudit(workspaceId: string): Promise<AuditLogPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/audit', {
      params: { path: { workspaceId } },
    }),
  );
}
