export type AuthUser = {
  id: string;
  email: string;
  name: string;
  emailVerified: boolean;
};

export type LoginResponse =
  | { status: 'authenticated'; user: AuthUser }
  | { status: 'two_factor_required'; challengeId: string };

export type RegisterResponse = {
  status: 'verification_required';
  email: string;
};

export type MessageResponse = {
  message: string;
};

export type AuthApiError = {
  title: string;
  detail: string;
  status: number;
};

const API_BASE = '/api/v1';

async function parseJson<T>(response: Response): Promise<T> {
  const data = (await response.json()) as T | AuthApiError;
  if (!response.ok) {
    const error = data as AuthApiError;
    throw new Error(error.detail || error.title || 'Request failed');
  }
  return data as T;
}

export async function loginRequest(body: {
  email: string;
  password: string;
}): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return parseJson<LoginResponse>(response);
}

export async function registerRequest(body: {
  name: string;
  email: string;
  password: string;
}): Promise<RegisterResponse> {
  const response = await fetch(`${API_BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return parseJson<RegisterResponse>(response);
}

export async function verifyEmailRequest(body: {
  email: string;
  code: string;
}): Promise<MessageResponse> {
  const response = await fetch(`${API_BASE}/auth/verify-email`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return parseJson<MessageResponse>(response);
}

export async function forgotPasswordRequest(body: {
  email: string;
}): Promise<MessageResponse> {
  const response = await fetch(`${API_BASE}/auth/forgot-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return parseJson<MessageResponse>(response);
}

export async function resetPasswordRequest(body: {
  token: string;
  password: string;
}): Promise<MessageResponse> {
  const response = await fetch(`${API_BASE}/auth/reset-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return parseJson<MessageResponse>(response);
}

export async function verifyTwoFactorRequest(body: {
  challengeId: string;
  code: string;
}): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE}/auth/2fa/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return parseJson<LoginResponse>(response);
}

export function oauthStartUrl(provider: 'vk' | 'yandex'): string {
  return `${API_BASE}/auth/oauth/${provider}`;
}
