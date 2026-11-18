import type {
  MessageResponse,
  SessionDto,
  TwoFactorConfirmRequest,
  TwoFactorDisableRequest,
  TwoFactorSetupResponse,
  UpdateMeRequest,
  UpdatePasswordRequest,
  UserProfile,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export async function updateMe(body: UpdateMeRequest): Promise<UserProfile> {
  const client = getApiClient();
  return unwrapApi(await client.PATCH('/api/v1/me', { body }));
}

export async function changePassword(body: UpdatePasswordRequest): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/me/password', { body }));
}

export async function listSessions(): Promise<SessionDto[]> {
  const client = getApiClient();
  const page = unwrapApi(await client.GET('/api/v1/me/sessions'));
  return page.items ?? [];
}

export async function revokeSession(sessionId: string): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.DELETE('/api/v1/me/sessions/{sessionId}', {
      params: { path: { sessionId } },
    }),
  );
}

export async function setupTwoFactor(): Promise<TwoFactorSetupResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/auth/2fa/setup'));
}

export async function confirmTwoFactor(body: TwoFactorConfirmRequest): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/auth/2fa/confirm', { body }));
}

export async function disableTwoFactor(body: TwoFactorDisableRequest): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/auth/2fa/disable', { body }));
}
