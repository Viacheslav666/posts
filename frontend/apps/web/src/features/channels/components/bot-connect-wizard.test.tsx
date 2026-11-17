import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { setChannelLimitOverride } from '@/mocks/channel-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { BotConnectWizard } from './bot-connect-wizard';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
}));

describe('BotConnectWizard (Telegram)', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    setChannelLimitOverride(PERSONAL_WORKSPACE_ID, null);
  });

  it('walks through TG connect confirm with username', async () => {
    const user = userEvent.setup();
    const onConnected = vi.fn();

    renderWithProviders(
      <BotConnectWizard
        network="tg"
        workspaceId={PERSONAL_WORKSPACE_ID}
        onConnected={onConnected}
      />,
    );

    expect(screen.getByTestId('bot-wizard-tg')).toBeInTheDocument();
    await user.click(screen.getByTestId('bot-wizard-start-tg'));

    await waitFor(() => {
      expect(screen.getByTestId('bot-username-tg')).toBeInTheDocument();
    });

    await user.type(screen.getByTestId('bot-username-tg'), '@brand_news');
    await user.click(screen.getByTestId('bot-wizard-confirm-tg'));

    await waitFor(() => {
      expect(onConnected).toHaveBeenCalled();
    });
  });

  it('shows plan limit when quota is exhausted', async () => {
    const user = userEvent.setup();
    const onPlanLimit = vi.fn();
    setChannelLimitOverride(PERSONAL_WORKSPACE_ID, 0);

    renderWithProviders(
      <BotConnectWizard
        network="tg"
        workspaceId={PERSONAL_WORKSPACE_ID}
        onPlanLimit={onPlanLimit}
      />,
    );

    await user.click(screen.getByTestId('bot-wizard-start-tg'));

    await waitFor(() => {
      expect(onPlanLimit).toHaveBeenCalled();
    });
  });
});
