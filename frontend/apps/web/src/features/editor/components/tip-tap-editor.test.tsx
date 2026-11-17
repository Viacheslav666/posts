import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { TipTapEditor } from './tip-tap-editor';

describe('TipTapEditor', () => {
  it('accepts keyboard input and toolbar formatting', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(
      <TipTapEditor aria-label="Текст поста" placeholder="Текст поста…" onChange={onChange} />,
    );

    const editor = await screen.findByRole('textbox', { name: 'Текст поста' });
    editor.focus();
    await user.keyboard('Posts');

    await waitFor(() => {
      expect(onChange).toHaveBeenCalled();
      const last = onChange.mock.calls.at(-1)?.[0] as { html: string };
      expect(last.html).toContain('Posts');
    });

    await user.keyboard('{Control>}a{/Control}');
    await user.click(screen.getByRole('button', { name: 'Жирный' }));

    await waitFor(() => {
      const last = onChange.mock.calls.at(-1)?.[0] as { html: string };
      expect(last.html).toMatch(/<b>Posts<\/b>|<strong>Posts<\/strong>/);
    });
  });
});
