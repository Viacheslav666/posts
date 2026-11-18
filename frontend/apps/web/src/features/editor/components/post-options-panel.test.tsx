import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { createDefaultPostOptions } from '@/features/editor/model/defaults';
import { renderWithProviders } from '@/test/render';

import { PostOptionsPanel } from './post-options-panel';

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
