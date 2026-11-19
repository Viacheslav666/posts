import type { Channel } from '@posts/api-client';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { AutomationChannelPicker } from './automation-channel-picker';

const channels: Channel[] = [
  {
    id: 'ch-vk',
    title: 'VK News',
    network: 'vk',
    status: 'active',
  },
  {
    id: 'ch-tg',
    title: 'TG Alerts',
    network: 'tg',
    status: 'active',
  },
  {
    id: 'ch-bad',
    title: 'VK Broken',
    network: 'vk',
    status: 'reauth_required',
  },
];

describe('AutomationChannelPicker', () => {
  it('selects capable channel and disables others with reason', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithProviders(
      <AutomationChannelPicker
        channels={channels}
        value={null}
        onChange={onChange}
        networks={['vk']}
      />,
    );

    const tg = screen.getByTestId('automation-channel-ch-tg');
    expect(tg).toBeDisabled();
    expect(tg).toHaveAttribute('title', expect.stringMatching(/не поддерживается/i));

    const broken = screen.getByTestId('automation-channel-ch-bad');
    expect(broken).toBeDisabled();

    await user.click(screen.getByTestId('automation-channel-ch-vk'));
    expect(onChange).toHaveBeenCalledWith('ch-vk');
  });

  it('filters by search query', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <AutomationChannelPicker channels={channels} onChange={vi.fn()} />,
    );
    await user.type(screen.getByLabelText('Канал'), 'Alerts');
    expect(screen.getByTestId('automation-channel-ch-tg')).toBeInTheDocument();
    expect(screen.queryByTestId('automation-channel-ch-vk')).not.toBeInTheDocument();
  });
});
