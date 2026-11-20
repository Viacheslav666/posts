import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { createDefaultPostOptions } from '@/features/editor/model/defaults';
import type { EditorPostOptions } from '@/features/editor/model/types';
import { renderWithProviders } from '@/test/render';

import { PostOptionsPanel } from './post-options-panel';

function PanelHarness({ initial }: { initial: EditorPostOptions }) {
  const [options, setOptions] = useState(initial);
  return <PostOptionsPanel options={options} networks={['vk']} onChange={setOptions} />;
}

describe('PostOptionsPanel (TC-AD-01)', () => {
  it('shows Donut/co-authors for VK and disables buttons', () => {
    renderWithProviders(
      <PostOptionsPanel
        options={createDefaultPostOptions()}
        networks={['vk']}
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByTestId('option-donut')).toBeEnabled();
    expect(screen.getByTestId('option-coowners')).toBeEnabled();
    expect(screen.getByTestId('option-buttons').closest('[data-unsupported]')).toBeTruthy();
  });

  it('enables inline buttons for Telegram', () => {
    renderWithProviders(
      <PostOptionsPanel
        options={createDefaultPostOptions()}
        networks={['tg']}
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByTestId('option-buttons').closest('[data-unsupported]')).toBeFalsy();
    expect(screen.getByTestId('option-donut').closest('[data-unsupported]')).toBeTruthy();
  });

  it('shows unpin field under pin and clears unpin when pin is off', async () => {
    const user = userEvent.setup();
    const base = createDefaultPostOptions();
    const initial = {
      ...base,
      variant: { ...base.variant, unpinAt: '2026-12-01T12:00:00.000Z' },
    };
    renderWithProviders(<PanelHarness initial={initial} />);

    expect(screen.queryByTestId('option-unpin-at')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('option-pin'));
    expect(screen.getByTestId('option-unpin-at')).toBeInTheDocument();

    await user.click(screen.getByTestId('option-pin'));
    await waitFor(() => expect(screen.queryByTestId('option-unpin-at')).not.toBeInTheDocument());
  });

  it('prefills deleteAfter hint for ad posts', () => {
    renderWithProviders(
      <PostOptionsPanel
        options={{ ...createDefaultPostOptions(), isAd: true }}
        networks={['vk']}
        isAd
        adAutoDeleteEnabled
        onChange={vi.fn()}
      />,
    );
    expect(
      screen.getByText('Если поле пусто, каждый слот удалится через 30 дн. после своей публикации'),
    ).toBeInTheDocument();
  });

  it('renders recurrence presets', () => {
    renderWithProviders(
      <PostOptionsPanel
        options={createDefaultPostOptions()}
        networks={['vk', 'tg']}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByTestId('recurrence-select')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Один раз' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'По будням' })).toBeInTheDocument();
  });
});
