import { beforeEach, describe, expect, it } from 'vitest';

import { CHANNEL_IDS } from './channel-store';
import {
  createCrosspostRule,
  getCrosspostRule,
  listCrosspostItems,
  listCrosspostRules,
  previewCrosspostRule,
  resetCrosspostStore,
  retryCrosspostItem,
  RULE_IDS,
  ITEM_IDS,
} from './crosspost-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from './workspace-store';

describe('crosspost-store (FE-24 MSW)', () => {
  beforeEach(() => {
    resetCrosspostStore();
  });

  it('seeds VK → TG + Max rule with journal items', () => {
    const rules = listCrosspostRules({
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
    });
    expect(rules.some((r) => r.id === RULE_IDS.vkToTgMax)).toBe(true);
    const { items } = listCrosspostItems(RULE_IDS.vkToTgMax);
    expect(items.length).toBeGreaterThanOrEqual(3);
    expect(items.some((i) => i.id === ITEM_IDS.failed1 && i.status === 'failed')).toBe(true);
  });

  it('creates rule and surfaces cycle_detected when graph loops', () => {
    // Existing: VK → TG, Max. Add TG → VK to form a cycle.
    const result = createCrosspostRule(PERSONAL_WORKSPACE_ID, DEFAULT_PROJECT_ID, {
      name: 'TG → VK loop',
      sourceChannelId: CHANNEL_IDS.tgNews,
      targets: [{ targetChannelId: CHANNEL_IDS.vkNews }],
      activate: true,
    });
    expect('id' in result).toBe(true);
    if ('id' in result) {
      expect(result.warnings?.some((w) => w.code === 'cycle_detected')).toBe(true);
    }
  });

  it('rejects source equal to target', () => {
    const result = createCrosspostRule(PERSONAL_WORKSPACE_ID, DEFAULT_PROJECT_ID, {
      name: 'Bad',
      sourceChannelId: CHANNEL_IDS.vkNews,
      targets: [{ targetChannelId: CHANNEL_IDS.vkNews }],
    });
    expect('code' in result && result.code === 'validation_failed').toBe(true);
  });

  it('previews without publishing', () => {
    const result = previewCrosspostRule(RULE_IDS.vkToTgMax, {
      sourceRemoteId: 'wall-1_2',
    });
    expect('targets' in result).toBe(true);
    if ('targets' in result) {
      expect(result.targets?.length).toBe(2);
    }
  });

  it('retries failed item to published', () => {
    const result = retryCrosspostItem(ITEM_IDS.failed1);
    expect('status' in result && result.status === 'published').toBe(true);
    const item = listCrosspostItems(RULE_IDS.vkToTgMax).items.find((i) => i.id === ITEM_IDS.failed1);
    expect(item?.status).toBe('published');
  });

  it('getCrosspostRule returns seeded rule', () => {
    expect(getCrosspostRule(RULE_IDS.vkToTgMax)?.name).toBe('VK → TG + Max');
  });
});
