import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { setAccessToken } from '@/shared/api-client/session';
import type { PermissionAction } from '@/shared/lib/permissions';

import messages from '@/messages/ru.json';

import type { EditorMediaItem } from '../model/types';
import { EditorMediaBlock } from './editor-media-block';

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => action === 'media.view' || action === 'media.manage',
}));

const overLimit: EditorMediaItem[] = Array.from({ length: 11 }, (_, index) => ({
  id: `e-${index}`,
  mediaId: `m-${index}`,
  kind: 'image',
  mime: 'image/jpeg',
  filename: `photo-${index}.jpg`,
  sizeBytes: 1024,
}));

describe('EditorMediaBlock', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
  });

  it('shows media limit validation errors', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <NextIntlClientProvider locale="ru" messages={messages}>
          <EditorMediaBlock
            workspaceId="019a0000-0002-7000-8000-000000000002"
            items={overLimit}
            selectedNetworks={['vk']}
            onChange={() => undefined}
          />
        </NextIntlClientProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByTestId('editor-media-block')).toBeInTheDocument();
    expect(await screen.findByTestId('editor-media-issues')).toBeInTheDocument();
    expect(screen.getByText(/не более 10 вложений/i)).toBeInTheDocument();
  });
});
