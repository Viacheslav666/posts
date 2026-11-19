import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { ScheduleWindowEditor } from './schedule-window-editor';

describe('ScheduleWindowEditor', () => {
  it('toggles weekday and adds interval', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithProviders(
      <ScheduleWindowEditor
        value={{ days: [1], intervals: [], timezone: 'Europe/Moscow' }}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByTestId('schedule-day-2'));
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ days: expect.arrayContaining([1, 2]) }),
    );

    await user.click(screen.getByTestId('schedule-add-interval'));
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        intervals: [{ start: '09:00', end: '18:00' }],
      }),
    );
  });
});
