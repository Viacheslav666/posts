import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import { listRules, resetGrabberStore } from '@/mocks/grabber-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { AutoModeWarning } from './auto-mode-warning';
import { RuleForm } from './rule-form';

const push = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
}));

const channels = [
  {
    id: CHANNEL_IDS.vkNews,
    workspaceId: PERSONAL_WORKSPACE_ID,
    network: 'vk' as const,
    title: 'Posts - новости',
    status: 'active' as const,
  },
];

describe('RuleForm (FE-14)', () => {
  beforeEach(() => {
    resetGrabberStore();
    setAccessToken('msw.access.token');
    push.mockReset();
  });

  it('defaults publish_mode to moderate and requires legal checkbox (TC-GRAB-08b/07)', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <RuleForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Основной' }}
        channels={channels}
      />,
    );

    expect(screen.getByTestId('grabber-publish-mode')).toHaveTextContent(/модерац/i);

    await user.type(screen.getByTestId('grabber-rule-name'), 'Тест правило');
    await user.type(screen.getByTestId('grabber-source-ids'), '-123, club_demo');
    await user.click(screen.getByTestId('grabber-rule-submit'));

    await waitFor(() => {
      expect(screen.getByText(/Нужно подтвердить ответственность/i)).toBeInTheDocument();
    });
  });

  it('creates rule after responsibility checkbox (TC-GRAB-01)', async () => {
    const user = userEvent.setup();
    const before = listRules({
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
    }).length;

    renderWithProviders(
      <RuleForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Основной' }}
        channels={channels}
      />,
    );

    await user.type(screen.getByTestId('grabber-rule-name'), 'VK grabber');
    await user.type(screen.getByTestId('grabber-source-ids'), '-123456');
    await user.click(screen.getByTestId('grabber-responsibility'));
    await user.click(screen.getByTestId('grabber-rule-submit'));

    await waitFor(() => {
      const after = listRules({
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
      }).length;
      expect(after).toBeGreaterThan(before);
    });
  });

  it('renders auto-publish warning component when visible', () => {
    const { rerender } = renderWithProviders(<AutoModeWarning visible={false} />);
    expect(screen.queryByTestId('grabber-auto-warning')).not.toBeInTheDocument();
    rerender(<AutoModeWarning visible />);
    expect(screen.getByTestId('grabber-auto-warning')).toBeInTheDocument();
  });

  it('renders FE-17 settings groups including VK publish options and copyright warning', async () => {
    renderWithProviders(
      <RuleForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Основной' }}
        channels={channels}
      />,
    );

    expect(screen.getByTestId('grabber-section-filters')).toBeInTheDocument();
    expect(screen.getByTestId('grabber-section-strip')).toBeInTheDocument();
    expect(screen.getByTestId('grabber-section-replacements')).toBeInTheDocument();
    expect(screen.getByTestId('grabber-section-schedule')).toBeInTheDocument();
    expect(screen.getByTestId('grabber-section-limits')).toBeInTheDocument();
    expect(screen.getByTestId('grabber-section-first-comment')).toBeInTheDocument();
    expect(screen.getByTestId('grabber-section-attached')).toBeInTheDocument();
    expect(screen.getByTestId('grabber-section-vk-publish')).toBeInTheDocument();
    expect(screen.getByTestId('grabber-only-these')).toBeInTheDocument();
    expect(screen.getByTestId('grabber-copyright-warning')).toHaveTextContent(/авторск/i);
    expect(screen.getByTestId('grabber-action-button-title')).toBeDisabled();
    expect(screen.getByTestId('grabber-action-button-hint')).toHaveTextContent(
      'Скоро: требует проверки на VK',
    );
    expect(screen.queryByTestId('pending-feature-notice')).not.toBeInTheDocument();
  });

  it('shows the group-id help link and disables interval in postponed mode', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <RuleForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Основной' }}
        channels={channels}
      />,
    );

    expect(screen.getByTestId('grabber-postponed-repost-unavailable')).toHaveTextContent(
      'Скоро: требует проверки на VK',
    );
    expect(screen.getByTestId('grabber-source-id-help')).toHaveTextContent('Как узнать ID группы?');
    expect(screen.getByTestId('grabber-target-mode')).toHaveTextContent(/Сразу на стену \(пост\)/);
    expect(screen.getByTestId('grabber-mode-capabilities')).toHaveAttribute('data-interval', 'true');
    expect(screen.getByTestId('grabber-max-age-days')).toHaveValue(60);
    expect(screen.getByTestId('grabber-notify-on-error')).toBeChecked();

    await user.click(screen.getByTestId('grabber-target-mode'));
    await user.click(await screen.findByRole('option', { name: 'В отложенные записи VK (пост)' }));

    expect(screen.getByTestId('grabber-min-interval')).toBeDisabled();
    expect(screen.getByTestId('grabber-min-interval-hint')).toHaveTextContent(
      'Интервал между постами недоступен в отложенных записях VK',
    );
    expect(screen.getByTestId('grabber-mode-capabilities')).toHaveAttribute('data-interval', 'false');
    expect(screen.getByTestId('grabber-mode-capabilities')).toHaveAttribute('data-action-button', 'false');
    expect(screen.getByTestId('grabber-mode-capabilities')).toHaveAttribute('data-first-comment', 'false');
    expect(screen.getByTestId('grabber-mode-capabilities')).toHaveAttribute(
      'data-comment-attachments',
      'false',
    );
    expect(screen.getByTestId('grabber-mode-capabilities')).toHaveAttribute('data-scheduled-delete', 'false');
  });
});
