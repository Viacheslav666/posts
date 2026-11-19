import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  resetSuggestStore,
  setSuggestSettingsWorkspace,
  SETTINGS_IDS,
} from '@/mocks/suggest-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { SettingsDetailPage } from './settings-detail-page';

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: () => true,
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}/automations/suggest/${SETTINGS_IDS.vkNews}`,
}));

describe('SettingsDetailPage workspace scope', () => {
  beforeEach(() => {
    resetSuggestStore();
    setAccessToken('msw.access.token');
  });

  it('does not open moderation for settings from another workspace', async () => {
    setSuggestSettingsWorkspace(SETTINGS_IDS.vkNews, '019a0000-0002-7000-8000-000000000099');

    renderWithProviders(
      <SettingsDetailPage workspaceId={PERSONAL_WORKSPACE_ID} settingsId={SETTINGS_IDS.vkNews} />,
    );

    expect(await screen.findByText('Не найдено')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByTestId('suggest-moderation-queue')).not.toBeInTheDocument();
    });
  });
});
