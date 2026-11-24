import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import {
  listCrosspostRules,
  resetCrosspostStore,
  RULE_IDS,
} from '@/mocks/crosspost-store';
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
  {
    id: CHANNEL_IDS.maxBrand,
    workspaceId: PERSONAL_WORKSPACE_ID,
    network: 'max' as const,
    title: 'Posts Max',
    status: 'active' as const,
    type: 'chat' as const,
  },
];

describe('RuleForm (FE-24)', () => {
  beforeEach(() => {
    resetCrosspostStore();
    setAccessToken('msw.access.token');
    push.mockReset();
  });

  it('walks wizard steps and creates VK → TG + Max rule', async () => {
    const user = userEvent.setup();
    const before = listCrosspostRules({
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

    expect(screen.getByTestId('crosspost-rule-form')).toBeInTheDocument();
    await user.clear(screen.getByTestId('crosspost-rule-name'));
    await user.type(screen.getByTestId('crosspost-rule-name'), 'Demo XP');
    await user.click(screen.getByTestId('crosspost-form-next'));

    await waitFor(() => {
      expect(screen.getByTestId('crosspost-step-panel-targets')).toBeInTheDocument();
    });
    await user.click(screen.getByTestId(`crosspost-target-${CHANNEL_IDS.tgNews}`));
    await user.click(screen.getByTestId(`crosspost-target-${CHANNEL_IDS.maxBrand}`));
    await user.click(screen.getByTestId('crosspost-form-next'));

    // filters → transform → schedule → review
    for (const panel of [
      'crosspost-step-panel-filters',
      'crosspost-step-panel-transform',
      'crosspost-step-panel-schedule',
      'crosspost-step-panel-review',
    ]) {
      await waitFor(() => {
        expect(screen.getByTestId(panel)).toBeInTheDocument();
      });
      if (panel !== 'crosspost-step-panel-review') {
        await user.click(screen.getByTestId('crosspost-form-next'));
      }
    }

    await user.click(screen.getByTestId('crosspost-rule-submit'));

    await waitFor(() => {
      const after = listCrosspostRules({
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
      }).length;
      expect(after).toBeGreaterThan(before);
      expect(push).toHaveBeenCalled();
    });
  });

  it('blocks source channel from target selection', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <RuleForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Основной' }}
        channels={channels}
      />,
    );

    await user.type(screen.getByTestId('crosspost-rule-name'), 'No self');
    await user.click(screen.getByTestId('crosspost-form-next'));

    await waitFor(() => {
      expect(screen.getByTestId('crosspost-step-panel-targets')).toBeInTheDocument();
    });
    expect(screen.queryByTestId(`crosspost-target-${CHANNEL_IDS.vkNews}`)).not.toBeInTheDocument();
  });

  it('shows cycle warning when initialWarnings include cycle_detected', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <RuleForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Основной' }}
        channels={channels}
        initialWarnings={[
          {
            code: 'cycle_detected',
            message: 'cycle',
            channelPath: [CHANNEL_IDS.vkNews, CHANNEL_IDS.tgNews, CHANNEL_IDS.vkNews],
          },
        ]}
      />,
    );

    await user.click(screen.getByTestId('crosspost-step-review'));
    expect(await screen.findByTestId('crosspost-cycle-warning')).toBeInTheDocument();
  });
});

describe('RuleForm edit existing', () => {
  beforeEach(() => {
    resetCrosspostStore();
    setAccessToken('msw.access.token');
  });

  it('loads existing rule into form', async () => {
    const { getCrosspostRule } = await import('@/mocks/crosspost-store');
    const rule = getCrosspostRule(RULE_IDS.vkToTgMax);
    expect(rule).toBeTruthy();

    renderWithProviders(
      <RuleForm
        workspaceId={PERSONAL_WORKSPACE_ID}
        project={{ id: DEFAULT_PROJECT_ID, workspaceId: PERSONAL_WORKSPACE_ID, name: 'Основной' }}
        channels={channels}
        rule={rule}
      />,
    );

    expect(screen.getByTestId('crosspost-rule-name')).toHaveValue('VK → TG + Max');
  });
});
