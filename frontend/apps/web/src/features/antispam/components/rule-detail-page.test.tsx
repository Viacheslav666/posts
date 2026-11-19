import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import { insertAntispamRule, resetAntispamStore, RULE_IDS } from '@/mocks/antispam-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { RuleDetailPage } from './rule-detail-page';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
}));

const FOREIGN_RULE = '019a0000-asrule-7000-8000-000000000099';

describe('RuleDetailPage (FE-23)', () => {
  beforeEach(() => {
    resetAntispamStore();
    setAccessToken('msw.access.token');
  });

  it('opens a rule of the current workspace', async () => {
    renderWithProviders(
      <RuleDetailPage workspaceId={PERSONAL_WORKSPACE_ID} ruleId={RULE_IDS.vkActive} />,
    );
    expect(await screen.findByTestId('antispam-rule-detail')).toBeInTheDocument();
    expect(screen.getByTestId('antispam-rule-form')).toBeInTheDocument();
  });

  it('does not show a rule that belongs to another workspace', async () => {
    insertAntispamRule({
      id: FOREIGN_RULE,
      workspaceId: '019a0000-ws-7000-8000-000000000099',
      projectId: DEFAULT_PROJECT_ID,
      channelId: CHANNEL_IDS.vkNews,
      name: 'Чужое правило',
      status: 'active',
      priority: 1,
      sections: ['wall_comment'],
      version: 1,
    });

    renderWithProviders(
      <RuleDetailPage workspaceId={PERSONAL_WORKSPACE_ID} ruleId={FOREIGN_RULE} />,
    );

    expect(await screen.findByText('Правило не найдено')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByTestId('antispam-rule-form')).not.toBeInTheDocument();
    });
    expect(screen.queryByText('Чужое правило')).not.toBeInTheDocument();
  });
});
