import { render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { server } from '@/mocks/server';
import { resetAuthRedirectForTests } from '@/shared/lib/auth-redirect';

import { SessionBootstrap } from './session-bootstrap';

let pathname = '/';

vi.mock('next/navigation', () => ({
  usePathname: () => pathname,
}));

describe('SessionBootstrap', () => {
  const assign = vi.fn();
  let refreshCalls = 0;

  beforeEach(() => {
    resetAuthRedirectForTests();
    assign.mockReset();
    refreshCalls = 0;
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        refreshCalls += 1;
        return HttpResponse.json(
          { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'unauthorized' },
          { status: 401 },
        );
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function stubLocation(path: string, search = '') {
    pathname = path;
    vi.stubGlobal('location', { ...window.location, pathname: path, search, assign });
  }

  it.each(['/auth/callback/vk', '/auth/callback/yandex'])(
    'renders %s without refreshing or redirecting to login',
    async (path) => {
      stubLocation(path, '?code=vk2.a&state=s&device_id=d');

      render(
        <SessionBootstrap>
          <p>callback content</p>
        </SessionBootstrap>,
      );

      expect(screen.getByText('callback content')).toBeInTheDocument();
      await new Promise((resolve) => setTimeout(resolve, 20));
      expect(refreshCalls).toBe(0);
      expect(assign).not.toHaveBeenCalled();
    },
  );

  it('redirects protected routes to login when refresh fails', async () => {
    stubLocation('/app/ws-1/posts');

    render(
      <SessionBootstrap>
        <p>workspace content</p>
      </SessionBootstrap>,
    );

    await waitFor(() => {
      expect(assign).toHaveBeenCalledWith(expect.stringContaining('reason=session_expired'));
    });
    expect(refreshCalls).toBe(1);
  });
});
