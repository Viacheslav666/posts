import type {
  LoginRequest,
  MessageResponse,
  OAuthStartResponse,
  RegisterRequest,
  TokenResponse,
  UserProfile,
} from '@posts/api-client';

import {
  applyTokenResponse,
  clearAccessToken,
  clearPendingLogin,
  getApiClient,
  savePendingLogin,
  unwrapApi,
} from '@/shared/api-client';
import { isTwoFactorRequired, toApiProblemError } from '@/shared/lib/errors';

export type AuthLoginResult =
  | { status: 'authenticated'; tokens: TokenResponse }
  | { status: 'two_factor_required' };

export async function loginRequest(body: LoginRequest): Promise<AuthLoginResult> {
  const client = getApiClient();
  const result = await client.POST('/api/v1/auth/login', { body });

  if (result.error || !result.response.ok) {
    const problem = toApiProblemError(result.error, result.response.status);
    if (isTwoFactorRequired(problem)) {
      savePendingLogin({ email: body.email, password: body.password });
      return { status: 'two_factor_required' };
    }
    throw problem;
  }

  const tokens = unwrapApi(result);
  applyTokenResponse(tokens);
  clearPendingLogin();
  return { status: 'authenticated', tokens };
}

export async function completeTwoFactorLogin(input: {
  email: string;
  password: string;
  totpCode: string;
}): Promise<TokenResponse> {
  const result = await loginRequest({
    email: input.email,
    password: input.password,
    totpCode: input.totpCode,
  });
  if (result.status !== 'authenticated') {
    throw new Error('Two-factor login failed');
  }
  return result.tokens;
}

export async function registerRequest(body: RegisterRequest): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/auth/register', { body }));
}

export async function verifyEmailRequest(body: { token: string }): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/auth/email/verify', { body }));
}

export async function forgotPasswordRequest(body: { email: string }): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/auth/password/forgot', { body }));
}

export async function resetPasswordRequest(body: {
  token: string;
  newPassword: string;
}): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/auth/password/reset', { body }));
}

export async function startOauth(provider: 'vk' | 'yandex' | 'telegram'): Promise<OAuthStartResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/auth/oauth/{provider}/start', {
      params: { path: { provider } },
    }),
  );
}

export async function fetchMe(): Promise<UserProfile> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/me'));
}

export async function logoutRequest(): Promise<void> {
  const client = getApiClient();
  try {
    await client.POST('/api/v1/auth/logout', { body: {} });
  } finally {
    clearAccessToken();
    clearPendingLogin();
  }
}
