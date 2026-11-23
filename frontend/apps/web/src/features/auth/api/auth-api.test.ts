import { beforeEach, describe, expect, it } from 'vitest';

import {
  clearAccessToken,
  clearPendingLogin,
  getAccessToken,
  readPendingLogin,
  resetApiClient,
} from '@/shared/api-client';
import { ApiProblemError } from '@/shared/lib/errors';

import { completeOauth, loginRequest, registerRequest, verifyEmailRequest } from './auth-api';

describe('auth-api (MSW + typed client)', () => {
  beforeEach(() => {
    resetApiClient();
    clearAccessToken();
    clearPendingLogin();
  });

  it('stores access token on successful login', async () => {
    const result = await loginRequest({
      email: 'demo@posts.app',
      password: 'Secret12ab',
    });
    expect(result.status).toBe('authenticated');
    expect(getAccessToken()).toBe('msw.access.token');
  });

  it('signals two-factor and keeps pending credentials', async () => {
    const result = await loginRequest({
      email: 'twofa@posts.app',
      password: 'Secret12ab',
    });
    expect(result).toEqual({ status: 'two_factor_required' });
    expect(readPendingLogin()?.email).toBe('twofa@posts.app');
    expect(getAccessToken()).toBeNull();
  });

  it('throws ApiProblemError with code on bad password', async () => {
    await expect(
      loginRequest({ email: 'demo@posts.app', password: 'wrong-password' }),
    ).rejects.toSatisfy((error: unknown) => {
      return (
        error instanceof ApiProblemError &&
        error.code === 'unauthorized' &&
        error.message === 'Invalid email or password'
      );
    });
  });

  it('completeOauth stores tokens after VK callback with device_id', async () => {
    await completeOauth('vk', {
      code: 'msw-code-vk',
      state: 'msw-state-vk',
      deviceId: 'msw-device',
    });
    expect(getAccessToken()).toBe('msw.access.token');
  });

  it('registers and verifies email via contract paths', async () => {
    const registered = await registerRequest({
      name: 'Demo',
      email: 'new@posts.app',
      password: 'Secret12ab',
    });
    expect(registered.message).toBe('verification_email_sent');

    const verified = await verifyEmailRequest({ token: '123456' });
    expect(verified.message).toBe('Email verified');
  });
});
