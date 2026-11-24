import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
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

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
}));

describe('PostEditor', () => {
  beforeEach(async () => {
    setAccessToken('msw.access.token');
    await clearDraft(PERSONAL_WORKSPACE_ID, 'test-draft');
    await clearDraft(PERSONAL_WORKSPACE_ID, 'test-draft-ad');
  });

  it('loads channel picker from /channels, variants and preview', async () => {
    renderWithProviders(
      <PostEditor workspaceId={PERSONAL_WORKSPACE_ID} draftId="test-draft" />,
    );

    expect(await screen.findByRole('heading', { name: 'Редактор поста' })).toBeInTheDocument();
    expect(screen.getByLabelText('Каналы публикации')).toBeInTheDocument();
    expect(screen.getByLabelText('Предпросмотр')).toBeInTheDocument();
    expect(screen.getByLabelText('Текст поста')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Общий текст' })).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Posts - новости')).toBeInTheDocument();
    });
  });

  it('supports keyboard navigation across toolbar and channel search', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <PostEditor workspaceId={PERSONAL_WORKSPACE_ID} draftId="test-draft" />,
    );

    await screen.findByLabelText('Текст поста');
    await waitFor(() => {
      expect(screen.getByText('Posts - новости')).toBeInTheDocument();
    });
    const search = screen.getByPlaceholderText('Поиск каналов');
    search.focus();
    expect(search).toHaveFocus();

    await user.tab();
    expect(document.activeElement).toHaveAttribute('type', 'checkbox');

    await user.keyboard(' ');
    await waitFor(() => {
      expect(screen.getByText(/Выбрано:/)).toBeInTheDocument();
    });
  });

  it('blocks publish when text exceeds Telegram limit', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <PostEditor workspaceId={PERSONAL_WORKSPACE_ID} draftId="test-draft" />,
    );

    await waitFor(() => {
      expect(screen.getByText('Posts - новости')).toBeInTheDocument();
    });
    await screen.findByText('Ошибок нет - можно планировать.');

    const editor = screen.getByTestId('mock-tiptap');
    await user.clear(editor);
    await user.paste('T'.repeat(4097));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Опубликовать сейчас' })).toBeDisabled();
  });

  it('shows post options and ad block; blocks schedule without erid (TC-AD-03)', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <PostEditor workspaceId={PERSONAL_WORKSPACE_ID} draftId="test-draft-ad" />,
    );

    expect(await screen.findByTestId('post-options-panel')).toBeInTheDocument();
    expect(screen.getByTestId('ad-marking-form')).toBeInTheDocument();
    expect(screen.getByTestId('option-donut')).toBeInTheDocument();

    await user.click(screen.getByTestId('ad-is-ad-toggle'));
    expect(await screen.findByTestId('ad-schedule-block')).toBeInTheDocument();
    expect(screen.getByTestId('editor-schedule')).toBeDisabled();
    expect(screen.getByTestId('ad-marking-slot')).toBeInTheDocument();
  });
});
