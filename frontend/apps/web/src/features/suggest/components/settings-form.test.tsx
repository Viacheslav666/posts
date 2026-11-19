import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import { getSuggestSettings, resetSuggestStore, SETTINGS_IDS } from '@/mocks/suggest-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { SettingsForm } from './settings-form';

const push = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}/automations/suggest/${SETTINGS_IDS.vkNews}`,
}));

describe('SettingsForm (FE-22)', () => {
  beforeEach(() => {
    resetSuggestStore();
    setAccessToken('msw.access.token');
    push.mockReset();
  });

  it('loads existing settings and saves stop word changes', async () => {
    const user = userEvent.setup();
    const settings = getSuggestSettings(SETTINGS_IDS.vkNews)!;

    renderWithProviders(
      <SettingsForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Demo' }}
        channels={[
          {
            id: CHANNEL_IDS.vkNews,
            network: 'vk',
            title: 'Posts — новости',
            status: 'active',
            capabilities: { publishSuggested: true } as never,
          },
        ]}
        settings={settings}
        canManage
      />,
    );

    expect(await screen.findByTestId('suggest-settings-form')).toBeInTheDocument();
    expect(screen.getByTestId('suggest-section-filters')).toBeInTheDocument();

    const stopInput = document.getElementById('suggest-stop-words') as HTMLInputElement | null;
    expect(stopInput).toBeTruthy();
    if (stopInput) {
      await user.clear(stopInput);
      await user.type(stopInput, 'реклама');
      await user.keyboard('{Enter}');
    }

    await user.click(screen.getByTestId('suggest-settings-save'));

    await waitFor(() => {
      expect(push).toHaveBeenCalled();
    });

    const updated = getSuggestSettings(SETTINGS_IDS.vkNews);
    const filters = (updated?.config as { filters?: { stop_words?: string[] } } | undefined)
      ?.filters;
    expect(filters?.stop_words?.some((word) => word.toLowerCase().includes('реклама'))).toBe(true);
  });

  it('renders create form with channel picker', async () => {
    renderWithProviders(
      <SettingsForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Demo' }}
        channels={[
          {
            id: CHANNEL_IDS.vkNews,
            network: 'vk',
            title: 'Posts — новости',
            status: 'active',
            capabilities: { publishSuggested: true } as never,
          },
        ]}
        canManage
      />,
    );

    expect(await screen.findByTestId('suggest-section-channel')).toBeInTheDocument();
    expect(screen.getByTestId('automation-channel-picker')).toBeInTheDocument();
  });
});
