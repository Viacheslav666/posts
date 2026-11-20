import { beforeEach, describe, expect, it, vi } from 'vitest';

import { XSRF_COOKIE_NAME, clearAccessToken, resetApiClient, setAccessToken } from '@/shared/api-client';

import { apiFetch } from './client';

describe('apiFetch', () => {
  beforeEach(() => {
    resetApiClient();
    clearAccessToken();
    document.cookie = `${XSRF_COOKIE_NAME}=fetch-xsrf; path=/`;
    vi.restoreAllMocks();
  });

  it('attaches X-XSRF-TOKEN and credentials on POST', async () => {
    setAccessToken('test.token');
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200 }),
    );

    await apiFetch('/api/v1/billing/mock/complete-payment', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ paymentId: 'p1', outcome: 'succeeded' }),
    });

    expect(fetchSpy).toHaveBeenCalledOnce();
    const [, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(init.headers);
    expect(headers.get('X-XSRF-TOKEN')).toBe('fetch-xsrf');
    expect(headers.get('Authorization')).toBe('Bearer test.token');
    expect(init.credentials).toBe('include');
  });
});