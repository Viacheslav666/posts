import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { ConnectFirstChannel } from './connect-first-channel';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
}));

describe('ConnectFirstChannel', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
  });

  it('opens TG wizard from network card', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ConnectFirstChannel workspaceId={PERSONAL_WORKSPACE_ID} />);

    expect(screen.getByTestId('connect-first-channel')).toBeInTheDocument();
    await user.click(screen.getByTestId('connect-network-tg'));
    expect(await screen.findByTestId('bot-wizard-tg')).toBeInTheDocument();
  });

  it('opens VK OAuth wizard from network card', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ConnectFirstChannel workspaceId={PERSONAL_WORKSPACE_ID} />);

    await user.click(screen.getByTestId('connect-network-vk'));
    expect(await screen.findByTestId('oauth-wizard-vk')).toBeInTheDocument();
  });
});
