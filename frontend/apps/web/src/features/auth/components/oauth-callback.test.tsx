import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { server } from '@/mocks/server';
import { getAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { OauthCallback } from './oauth-callback';

const replace = vi.fn();
let searchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace, prefetch: vi.fn() }),
  useSearchParams: () => searchParams,
}));

describe('OauthCallback', () => {
  beforeEach(() => {
    replace.mockReset();
  });

  it('exchanges VK ID code, state and device_id, stores the session and enters the app', async () => {
    const seen: URLSearchParams[] = [];
    server.events.on('request:start', ({ request }) => {
      if (request.url.includes('/api/v1/auth/oauth/vk/callback')) {
        seen.push(new URL(request.url).searchParams);
      }
    });
    searchParams = new URLSearchParams({
      code: 'vk2.a.code',
      expires_in: '600',
      device_id: 'msw-device',
      state: 'msw-state-vk',
      ext_id: 'ext',
      type: 'code_v2',
    });

    renderWithProviders(<OauthCallback provider="vk" />);

    await waitFor(() => {
      expect(replace).toHaveBeenCalledWith('/app/019a0000-0002-7000-8000-000000000002');
    });
    server.events.removeAllListeners();
    expect(seen).toHaveLength(1);
    expect(seen[0].get('code')).toBe('vk2.a.code');
    expect(seen[0].get('state')).toBe('msw-state-vk');
    expect(seen[0].get('device_id')).toBe('msw-device');
    expect(getAccessToken()).toBeTruthy();
  });

  it('completes Yandex ID login without device_id', async () => {
    searchParams = new URLSearchParams({ code: 'ya-code', state: 'msw-state-yandex' });

    renderWithProviders(<OauthCallback provider="yandex" />);

    await waitFor(() => {
      expect(replace).toHaveBeenCalledWith('/app/019a0000-0002-7000-8000-000000000002');
    });
  });

  it('shows a clear error when the login link is expired or reused', async () => {
    server.use(
      http.post('*/api/v1/auth/oauth/:provider/callback', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Unauthorized', status: 401, detail: 'Invalid OAuth state', code: 'unauthorized' },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    searchParams = new URLSearchParams({ code: 'c', state: 'stale', device_id: 'd' });

    renderWithProviders(<OauthCallback provider="vk" />);

    expect(await screen.findByText(/Ссылка для входа устарела/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Вернуться ко входу' })).toHaveAttribute('href', '/login');
    expect(replace).not.toHaveBeenCalled();
  });

  it('explains when an account with the same email must be linked first', async () => {
    searchParams = new URLSearchParams({ code: 'msw-link-required', state: 'msw-state-vk', device_id: 'd' });

    renderWithProviders(<OauthCallback provider="vk" />);

    expect(await screen.findByText('Аккаунт с этой почтой уже есть')).toBeInTheDocument();
  });

  it('shows provider errors such as access_denied without calling the API', async () => {
    const callbackHit = vi.fn();
    server.use(
      http.post('*/api/v1/auth/oauth/:provider/callback', () => {
        callbackHit();
        return HttpResponse.json({});
      }),
    );
    searchParams = new URLSearchParams({ error: 'access_denied', state: 'msw-state-vk' });

    renderWithProviders(<OauthCallback provider="vk" />);

    expect(await screen.findByText(/Вы отменили вход/)).toBeInTheDocument();
    expect(callbackHit).not.toHaveBeenCalled();
  });

  it('shows an error when code or state is missing', async () => {
    searchParams = new URLSearchParams({ device_id: 'd' });

    renderWithProviders(<OauthCallback provider="vk" />);

    expect(await screen.findByText(/В ссылке нет данных для входа/)).toBeInTheDocument();
  });

  it('rejects unsupported providers', async () => {
    searchParams = new URLSearchParams({ code: 'c', state: 's' });

    renderWithProviders(<OauthCallback provider="telegram" />);

    expect(await screen.findByText('Этот способ входа не поддерживается.')).toBeInTheDocument();
  });
});
