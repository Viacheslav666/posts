import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NextIntlClientProvider } from 'next-intl';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { setAccessToken } from '@/shared/api-client/session';
import type { PermissionAction } from '@/shared/lib/permissions';

import messages from '@/messages/ru.json';

import { MediaLibraryPage } from './media-library-page';

const canMap: Partial<Record<PermissionAction, boolean>> = {
  'media.view': true,
  'media.manage': true,
};

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => canMap[action] ?? false,
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <NextIntlClientProvider locale="ru" messages={messages}>
        <MediaLibraryPage workspaceId="019a0000-0002-7000-8000-000000000002" />
      </NextIntlClientProvider>
    </QueryClientProvider>,
  );
}

describe('MediaLibraryPage', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    canMap['media.view'] = true;
    canMap['media.manage'] = true;
  });

  it('renders library grid with seeded assets and quota', async () => {
    renderPage();
    expect(await screen.findByTestId('media-library-page')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Медиатека' })).toBeInTheDocument();
    expect(screen.getByTestId('media-quota')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId('media-grid')).toBeInTheDocument();
    });
    expect(screen.getByText('cover-autumn.jpg')).toBeInTheDocument();
  });

  it('filters by kind via search query', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByTestId('media-library-page');
    const search = screen.getByLabelText('Поиск по имени и тегам');
    await user.clear(search);
    await user.type(search, 'story-reel');
    await waitFor(() => {
      expect(screen.getByText('story-reel.mp4')).toBeInTheDocument();
      expect(screen.queryByText('cover-autumn.jpg')).not.toBeInTheDocument();
    });
  });

  it('hides library when media.view is denied', async () => {
    canMap['media.view'] = false;
    renderPage();
    expect(await screen.findByText('Нет доступа')).toBeInTheDocument();
  });
});
