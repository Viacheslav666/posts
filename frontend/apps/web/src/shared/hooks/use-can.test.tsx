import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import messages from '@/messages/ru.json';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';

import { useCan } from './use-can';

vi.mock('next/navigation', () => ({
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return (
    <NextIntlClientProvider locale="ru" messages={messages}>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </NextIntlClientProvider>
  );
}

describe('useCan', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
  });

  it('allows members.invite for owner membership', async () => {
    const { result } = renderHook(() => useCan('members.invite', PERSONAL_WORKSPACE_ID), {
      wrapper,
    });

    await waitFor(() => {
      expect(result.current).toBe(true);
    });
  });

  it('allows projects.view for owner', async () => {
    const { result } = renderHook(() => useCan('projects.view'), { wrapper });

    await waitFor(() => {
      expect(result.current).toBe(true);
    });
  });
});
