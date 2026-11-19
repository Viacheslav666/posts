import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import { RULE_IDS, resetAntispamStore } from '@/mocks/antispam-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { ScanPanel } from './scan-panel';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
}));

describe('ScanPanel (FE-23)', () => {
  beforeEach(() => {
    resetAntispamStore();
    setAccessToken('msw.access.token');
  });

  it('runs VK scan and shows result', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ScanPanel
        ruleId={RULE_IDS.vkActive}
        canModerate
        channel={{
          id: CHANNEL_IDS.vkNews,
          network: 'vk',
          title: 'VK',
          status: 'active',
        }}
      />,
    );

    expect(screen.getByTestId('antispam-scan-panel')).toBeInTheDocument();
    await user.click(screen.getByTestId('antispam-scan-submit'));
    await waitFor(() => {
      expect(screen.getByTestId('antispam-scan-result')).toBeInTheDocument();
    });
  });

  it('disables scan for Telegram', () => {
    renderWithProviders(
      <ScanPanel
        ruleId={RULE_IDS.tgActive}
        canModerate
        channel={{
          id: CHANNEL_IDS.tgNews,
          network: 'tg',
          title: 'TG',
          status: 'active',
        }}
      />,
    );

    expect(screen.getByTestId('antispam-scan-tg-disabled')).toBeInTheDocument();
    expect(screen.queryByTestId('antispam-scan-submit')).not.toBeInTheDocument();
  });
});
