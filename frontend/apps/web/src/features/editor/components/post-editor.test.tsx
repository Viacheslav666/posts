import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { clearDraft } from '../model/draft-db';
import { PostEditor } from './post-editor';

vi.mock('./tip-tap-editor', () => ({
  TipTapEditor: ({
    onChange,
    'aria-label': ariaLabel,
  }: {
    onChange: (payload: { html: string; doc: { type: string; content: unknown[] } }) => void;
    'aria-label'?: string;
  }) => (
    <textarea
      aria-label={ariaLabel}
      data-testid="mock-tiptap"
      onChange={(event) =>
        onChange({
          html: event.target.value,
          doc: {
            type: 'doc',
            content: [{ type: 'paragraph', content: [{ type: 'text', text: event.target.value }] }],
          },
        })
      }
    />
  ),
}));

describe('PostEditor', () => {
  beforeEach(async () => {
    await clearDraft('demo', 'test-draft');
  });

  it('loads channel picker, variants and preview', async () => {
    renderWithProviders(<PostEditor workspaceId="demo" draftId="test-draft" />);

    expect(await screen.findByRole('heading', { name: 'Редактор поста' })).toBeInTheDocument();
    expect(screen.getByLabelText('Каналы публикации')).toBeInTheDocument();
    expect(screen.getByLabelText('Предпросмотр')).toBeInTheDocument();
    expect(screen.getByLabelText('Текст поста')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Общий текст' })).toBeInTheDocument();
  });

  it('supports keyboard navigation across toolbar and channel search', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PostEditor workspaceId="demo" draftId="test-draft" />);

    await screen.findByLabelText('Текст поста');
    const search = screen.getByPlaceholderText('Поиск каналов');
    search.focus();
    expect(search).toHaveFocus();

    await user.tab();
    // First channel checkbox after search
    expect(document.activeElement).toHaveAttribute('type', 'checkbox');

    await user.keyboard(' ');
    await waitFor(() => {
      expect(screen.getByText(/Выбрано:/)).toBeInTheDocument();
    });
  });

  it('blocks publish when text exceeds Telegram limit', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PostEditor workspaceId="demo" draftId="test-draft" />);

    await screen.findByText('Ошибок нет — можно планировать.');

    const editor = screen.getByTestId('mock-tiptap');
    await user.clear(editor);
    await user.paste('T'.repeat(4097));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Опубликовать сейчас' })).toBeDisabled();
  });

  it('switches preview network with keyboard-activatable tabs', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PostEditor workspaceId="demo" draftId="test-draft" />);

    const maxTab = await screen.findByRole('tab', { name: 'Max' });
    await user.click(maxTab);
    expect(maxTab).toHaveAttribute('aria-selected', 'true');
  });
});
