import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { LimitInput } from './limit-input';

describe('LimitInput', () => {
  it('updates amount on input', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithProviders(
      <LimitInput value={{ amount: null, period: 'hour' }} onChange={onChange} />,
    );
    await user.type(screen.getByLabelText('Количество'), '5');
    expect(onChange).toHaveBeenCalled();
    const last = onChange.mock.calls.at(-1)?.[0];
    expect(last).toMatchObject({ amount: 5, period: 'hour' });
  });
});
