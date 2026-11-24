import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { server } from '@/mocks/server';
import { renderWithProviders } from '@/test/render';

import { OauthCallbackPage } from './oauth-callback-page';

const push = vi.fn();
let searchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), prefetch: vi.fn() }),
  useSearchParams: () => searchParams,
}));

describe('OauthCallbackPage', () => {
  beforeEach(() => {
    push.mockReset();
  });

  it('forwards device_id to channel connect callback and returns to channels', async () => {
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/channels/connect/vk/callback', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({
          items: [
            {
              id: '019a0000-0001-7000-8000-000000000099',
              workspaceId: '019a0000-0002-7000-8000-000000000002',
              network: 'vk',
              status: 'active',
              type: 'group',
              title: 'G',
              externalId: '-1',
            },
          ],
          hasMore: false,
          workspaceId: '019a0000-0002-7000-8000-000000000002',
        });
      }),
    );

    searchParams = new URLSearchParams({
      code: 'vk-code',
      state: 'connect-state',
      device_id: 'vk-device',
    });

    renderWithProviders(<OauthCallbackPage network="vk" />);

    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toMatchObject({
      code: 'vk-code',
      state: 'connect-state',
      deviceId: 'vk-device',
    });
    expect(await screen.findByTestId('oauth-callback-done')).toBeInTheDocument();
  });

  it('shows Russian error when VK returns access_denied', async () => {
    searchParams = new URLSearchParams({ error: 'access_denied' });
    renderWithProviders(<OauthCallbackPage network="vk" workspaceId="019a0000-0002-7000-8000-000000000002" />);
    expect(await screen.findByText(/Вы отменили авторизацию VK/)).toBeInTheDocument();
  });
});