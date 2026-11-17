import { http, HttpResponse } from 'msw';

type RegisterBody = {
  name?: string;
  email?: string;
  password?: string;
};

type LoginBody = {
  email?: string;
  password?: string;
};

type CodeBody = {
  email?: string;
  code?: string;
  challengeId?: string;
  token?: string;
  password?: string;
};

const DEMO_USER = {
  id: 'usr_demo_001',
  email: 'demo@posts.app',
  name: 'Demo User',
  emailVerified: true,
};

export const handlers = [
  http.post('/api/v1/auth/register', async ({ request }) => {
    const body = (await request.json()) as RegisterBody;
    if (!body.email || !body.password || !body.name) {
      return HttpResponse.json(
        {
          title: 'Validation failed',
          detail: 'name, email and password are required',
          status: 400,
        },
        { status: 400 },
      );
    }
    if (body.email.toLowerCase() === 'taken@posts.app') {
      return HttpResponse.json(
        {
          title: 'Conflict',
          detail: 'Email already registered',
          status: 409,
        },
        { status: 409 },
      );
    }
    return HttpResponse.json(
      {
        status: 'verification_required',
        email: body.email,
      },
      { status: 201 },
    );
  }),

  http.post('/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as LoginBody;
    if (!body.email || !body.password) {
      return HttpResponse.json(
        {
          title: 'Validation failed',
          detail: 'email and password are required',
          status: 400,
        },
        { status: 400 },
      );
    }
    if (body.password === 'wrong-password') {
      return HttpResponse.json(
        {
          title: 'Unauthorized',
          detail: 'Invalid email or password',
          status: 401,
        },
        { status: 401 },
      );
    }
    if (body.email.toLowerCase() === 'twofa@posts.app') {
      return HttpResponse.json({
        status: 'two_factor_required',
        challengeId: 'chal_demo_2fa',
      });
    }
    return HttpResponse.json({
      status: 'authenticated',
      user: { ...DEMO_USER, email: body.email },
    });
  }),

  http.post('/api/v1/auth/verify-email', async ({ request }) => {
    const body = (await request.json()) as CodeBody;
    if (body.code !== '123456') {
      return HttpResponse.json(
        {
          title: 'Invalid code',
          detail: 'Verification code is incorrect or expired',
          status: 400,
        },
        { status: 400 },
      );
    }
    return HttpResponse.json({ message: 'Email verified' });
  }),

  http.post('/api/v1/auth/forgot-password', async ({ request }) => {
    const body = (await request.json()) as CodeBody;
    if (!body.email) {
      return HttpResponse.json(
        {
          title: 'Validation failed',
          detail: 'email is required',
          status: 400,
        },
        { status: 400 },
      );
    }
    return HttpResponse.json({
      message: 'If the account exists, a reset link was sent',
    });
  }),

  http.post('/api/v1/auth/reset-password', async ({ request }) => {
    const body = (await request.json()) as CodeBody;
    if (!body.token || !body.password) {
      return HttpResponse.json(
        {
          title: 'Validation failed',
          detail: 'token and password are required',
          status: 400,
        },
        { status: 400 },
      );
    }
    if (body.token === 'expired') {
      return HttpResponse.json(
        {
          title: 'Gone',
          detail: 'Reset token is invalid or expired',
          status: 410,
        },
        { status: 410 },
      );
    }
    return HttpResponse.json({ message: 'Password updated' });
  }),

  http.post('/api/v1/auth/2fa/verify', async ({ request }) => {
    const body = (await request.json()) as CodeBody;
    if (!body.challengeId || body.code !== '654321') {
      return HttpResponse.json(
        {
          title: 'Invalid code',
          detail: 'Two-factor code is incorrect',
          status: 401,
        },
        { status: 401 },
      );
    }
    return HttpResponse.json({
      status: 'authenticated',
      user: DEMO_USER,
    });
  }),

  http.get('/api/v1/auth/oauth/:provider', ({ params }) => {
    const provider = String(params.provider);
    return HttpResponse.json({
      status: 'redirect_stub',
      provider,
      authorizeUrl: `https://oauth.example/${provider}/authorize?client_id=posts-mock`,
    });
  }),
];
