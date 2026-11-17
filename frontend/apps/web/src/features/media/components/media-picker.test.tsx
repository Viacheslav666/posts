import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NextIntlClientProvider } from 'next-intl';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { setAccessToken } from '@/shared/api-client/session';
import type { PermissionAction } from '@/shared/lib/permissions';

import messages from '@/messages/ru.json';

import { MediaPicker } from './media-picker';

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => action === 'media.view' || action === 'media.manage',
}));

describe('MediaPicker', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
  });

  it('selects ready assets and confirms', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={client}>
        <NextIntlClientProvider locale="ru" messages={messages}>
          <MediaPicker
            workspaceId="019a0000-0002-7000-8000-000000000002"
            open
            onOpenChange={() => undefined}
            onConfirm={onConfirm}
          />
        </NextIntlClientProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByTestId('media-picker')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText('cover-autumn.jpg')).toBeInTheDocument();
    });

    await user.click(screen.getByTestId('media-card-019a0000-00m0-7000-8000-000000000001'));
    await user.click(screen.getByTestId('media-picker-confirm'));
    expect(onConfirm).toHaveBeenCalled();
    expect(onConfirm.mock.calls[0][0][0].filename).toBe('cover-autumn.jpg');
  });
});
