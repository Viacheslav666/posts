import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import {
  getAntispamRule,
  listAntispamRules,
  resetAntispamStore,
  RULE_IDS,
} from '@/mocks/antispam-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

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
    type: 'group' as const,
  },
  {
    id: CHANNEL_IDS.tgNews,
    workspaceId: PERSONAL_WORKSPACE_ID,
    network: 'tg' as const,
    title: '@posts_news',
    status: 'active' as const,
    type: 'channel' as const,
  },
];

describe('RuleForm (FE-23)', () => {
  beforeEach(() => {
    resetAntispamStore();
    setAccessToken('msw.access.token');
    push.mockReset();
  });

  it('creates a VK rule with stop-word and activates', async () => {
    const user = userEvent.setup();
    const before = listAntispamRules({
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

    expect(screen.getByTestId('antispam-rule-form')).toBeInTheDocument();
    await user.clear(screen.getByTestId('antispam-rule-name'));
    await user.type(screen.getByTestId('antispam-rule-name'), 'E2E стоп');

    const keywordInput = screen.getAllByPlaceholderText(/Слово или список/i)[0]!;
    await user.type(keywordInput, 'спам');
    await user.click(screen.getAllByRole('button', { name: 'Добавить' })[0]!);

    await user.click(screen.getByTestId('antispam-rule-submit'));

    await waitFor(() => {
      expect(push).toHaveBeenCalled();
    });

    const after = listAntispamRules({
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
    });
    expect(after.length).toBeGreaterThan(before);
    expect(after.some((r) => r.name === 'E2E стоп')).toBe(true);
  });

  it('saves match mode and does not drop domains or unknown config keys', async () => {
    const user = userEvent.setup();
    const rule = getAntispamRule(RULE_IDS.vkActive);
    expect(rule).toBeTruthy();
    const editable = {
      ...rule!,
      config: {
        filters: {
          keywords: {
            black: ['спам'],
            white: [],
            black_list_ids: [],
            white_list_ids: [],
            match: 'whole_word',
            legacy_flag: true,
          },
          links: { all: false, vk: false, domain_whitelist: ['example.com'] },
        },
        exceptions: { admin_ids: ['42'], user_whitelist: ['7'], exclude_own_community: true },
        custom: { keep: 1 },
      } as never,
    };

    renderWithProviders(
      <RuleForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Основной' }}
        channels={channels}
        rule={editable}
      />,
    );

    await user.type(screen.getByTestId('antispam-domain-whitelist'), ',vk.com');
    await user.click(screen.getByTestId('antispam-rule-submit'));

    await waitFor(() => {
      expect(push).toHaveBeenCalled();
    });

    const saved = getAntispamRule(RULE_IDS.vkActive);
    const config = saved?.config as {
      filters?: {
        keywords?: { match?: string; black?: string[]; legacy_flag?: boolean };
        links?: { domain_whitelist?: string[] };
      };
      exceptions?: { admin_ids?: string[] };
      custom?: { keep?: number };
    };
    expect(config.filters?.keywords?.match).toBe('whole_word');
    expect(config.filters?.keywords?.black).toEqual(['спам']);
    expect(config.filters?.keywords?.legacy_flag).toBe(true);
    expect(config.filters?.links?.domain_whitelist).toEqual(['example.com', 'vk.com']);
    expect(config.exceptions?.admin_ids).toEqual(['42']);
    expect(config.custom).toEqual({ keep: 1 });
  });

  it('toggles dry-run explanation', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <RuleForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Основной' }}
        channels={channels}
      />,
    );

    expect(screen.queryByTestId('antispam-dry-run-explain')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('antispam-dry-run'));
    expect(screen.getByTestId('antispam-dry-run-explain')).toBeInTheDocument();
  });

  it('shows TG sender_chat when channel is Telegram', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <RuleForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Основной' }}
        channels={channels}
      />,
    );

    await user.click(screen.getByTestId(`automation-channel-${CHANNEL_IDS.tgNews}`));

    await waitFor(() => {
      expect(screen.getByTestId('antispam-authors-sender-chat')).toBeInTheDocument();
    });
    expect(screen.getByTestId('antispam-section-tg_discussion')).toBeInTheDocument();
  });
});
