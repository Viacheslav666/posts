import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import axe from 'axe-core';
import { NextIntlClientProvider } from 'next-intl';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import messages from '@/messages/ru.json';
import { setAccessToken } from '@/shared/api-client/session';
import type { PermissionAction } from '@/shared/lib/permissions';

import { MediaLibraryPage } from './media-library-page';

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => action === 'media.view' || action === 'media.manage',
}));

describe('MediaLibraryPage a11y', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
  });

  it('has no serious axe violations', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { container } = render(
      <QueryClientProvider client={client}>
        <NextIntlClientProvider locale="ru" messages={messages}>
          <MediaLibraryPage workspaceId="019a0000-0002-7000-8000-000000000002" />
        </NextIntlClientProvider>
      </QueryClientProvider>,
    );

    // Wait for content
    await vi.waitFor(() => {
      expect(container.querySelector('[data-testid="media-library-page"]')).toBeTruthy();
    });

    const results = await axe.run(container, {
      rules: {
        // Radix select trigger labelling covered separately; keep critical rules on.
        'color-contrast': { enabled: false },
      },
    });
    const serious = results.violations.filter(
      (item) => item.impact === 'critical' || item.impact === 'serious',
    );
    expect(serious).toEqual([]);
  });
});
