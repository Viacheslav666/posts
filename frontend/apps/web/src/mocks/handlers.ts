import type {
  EmailVerifyRequest,
  LoginRequest,
  OAuthStartResponse,
  PasswordForgotRequest,
  PasswordResetRequest,
  ProblemDetail,
  RegisterRequest,
  TokenResponse,
  UserProfile,
} from '@posts/api-client';
import { http, HttpResponse } from 'msw';

import { TWO_FACTOR_REQUIRED_CODE } from '@/shared/lib/errors';

const DEMO_PROFILE: UserProfile = {
  id: '019a0000-0002-7000-8000-000000000001',
  email: 'demo@posts.app',
  emailVerified: true,
  name: 'Demo User',
  timezone: 'Europe/Moscow',
  locale: 'ru',
  totpEnabled: false,
  memberships: [
    {
      workspaceId: '019a0000-0002-7000-8000-000000000002',
      workspaceName: 'Личное пространство',
      role: 'owner',
      billingStatus: 'trial',
    },
  ],
};

function problem(status: number, code: string, detail: string, title?: string) {
  const body: ProblemDetail = {
    type: `https://posts.ru/problems/${code}`,
    title: title ?? code,
    status,
    detail,
    code,
    instance: '/api/v1/auth',
  };
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function tokenResponse(overrides?: Partial<TokenResponse>): TokenResponse {
  return {
    accessToken: 'msw.access.token',
    tokenType: 'Bearer',
    expiresIn: 900,
    ...overrides,
  };
}

function refreshCookieHeader(): string {
  return 'refresh_token=msw.refresh.token; Path=/api/v1/auth; HttpOnly; SameSite=Lax';
}

/**
 * MSW handlers typed against `docs/api/openapi.yaml` schemas.
 * Paths and bodies match BE-3 contract; auth behaviour is mocked for FE until BE-5.
 */
export const handlers = [
  http.get('/api/v1/ping', () => HttpResponse.json({ message: 'pong' })),

  http.get('/api/v1/me', ({ request }) => {
    const auth = request.headers.get('Authorization');
    if (!auth?.startsWith('Bearer ')) {
      return problem(401, 'unauthorized', 'Missing or invalid access token', 'Unauthorized');
    }
    return HttpResponse.json(DEMO_PROFILE);
  }),

  http.post('/api/v1/auth/register', async ({ request }) => {
    const body = (await request.json()) as RegisterRequest;
    if (!body.email || !body.password || !body.name) {
      return problem(400, 'validation_failed', 'name, email and password are required', 'Validation failed');
    }
    if (body.password.length < 10) {
      return problem(400, 'validation_failed', 'password must be at least 10 characters', 'Validation failed');
    }
    if (body.email.toLowerCase() === 'taken@posts.app') {
      return problem(409, 'conflict', 'Email already registered', 'Conflict');
    }
    return HttpResponse.json({ message: 'verification_email_sent' }, { status: 201 });
  }),

  http.post('/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as LoginRequest;
    if (!body.email || !body.password) {
      return problem(400, 'validation_failed', 'email and password are required', 'Validation failed');
    }
    if (body.password === 'wrong-password') {
      return problem(401, 'unauthorized', 'Invalid email or password', 'Unauthorized');
    }

    const email = body.email.toLowerCase();
    if (email === 'twofa@posts.app') {
      if (!body.totpCode) {
        // Provisional until BE-5 adds the code to ProblemCodes / OpenAPI catalog.
        return problem(
          401,
          TWO_FACTOR_REQUIRED_CODE,
          'TOTP code required',
          'Two-factor required',
        );
      }
      if (body.totpCode !== '654321') {
        return problem(401, 'unauthorized', 'Two-factor code is incorrect', 'Unauthorized');
      }
    }

    return HttpResponse.json(tokenResponse(), {
      headers: { 'Set-Cookie': refreshCookieHeader() },
    });
  }),

  http.post('/api/v1/auth/refresh', () =>
    HttpResponse.json(tokenResponse({ accessToken: 'msw.access.refreshed' }), {
      headers: { 'Set-Cookie': refreshCookieHeader() },
    }),
  ),

  http.post('/api/v1/auth/logout', () =>
    HttpResponse.json(
      { message: 'ok' },
      {
        headers: {
          'Set-Cookie': 'refresh_token=; Path=/api/v1/auth; Max-Age=0; HttpOnly; SameSite=Lax',
        },
      },
    ),
  ),

  http.post('/api/v1/auth/email/verify', async ({ request }) => {
    const body = (await request.json()) as EmailVerifyRequest;
    if (!body.token) {
      return problem(400, 'validation_failed', 'token is required', 'Validation failed');
    }
    if (body.token !== '123456' && body.token !== 'demo-verify-token') {
      return problem(400, 'validation_failed', 'Verification token is incorrect or expired', 'Invalid token');
    }
    return HttpResponse.json({ message: 'Email verified' });
  }),

  http.post('/api/v1/auth/password/forgot', async ({ request }) => {
    const body = (await request.json()) as PasswordForgotRequest;
    if (!body.email) {
      return problem(400, 'validation_failed', 'email is required', 'Validation failed');
    }
    return HttpResponse.json({
      message: 'If the account exists, a reset link was sent',
    });
  }),

  http.post('/api/v1/auth/password/reset', async ({ request }) => {
    const body = (await request.json()) as PasswordResetRequest;
    if (!body.token || !body.newPassword) {
      return problem(400, 'validation_failed', 'token and newPassword are required', 'Validation failed');
    }
    if (body.newPassword.length < 10) {
      return problem(400, 'validation_failed', 'password must be at least 10 characters', 'Validation failed');
    }
    if (body.token === 'expired') {
      return problem(410, 'not_found', 'Reset token is invalid or expired', 'Gone');
    }
    return HttpResponse.json({ message: 'Password updated' });
  }),

  http.post('/api/v1/auth/oauth/:provider/start', ({ params }) => {
    const provider = String(params.provider);
    if (provider !== 'vk' && provider !== 'yandex' && provider !== 'telegram') {
      return problem(400, 'validation_failed', `Unknown OAuth provider: ${provider}`, 'Validation failed');
    }
    const payload: OAuthStartResponse = {
      authorizationUrl: `https://oauth.example/${provider}/authorize?client_id=posts-mock`,
      state: `msw-state-${provider}`,
    };
    return HttpResponse.json(payload);
  }),
];
