import { beforeEach, describe, expect, it } from 'vitest';

import { CHANNEL_IDS } from './channel-store';
import {
  activateAntispamRule,
  createAntispamRule,
  getAntispamRule,
  getAntispamRuleStats,
  listAntispamLog,
  listAntispamRules,
  LOG_IDS,
  pauseAntispamRule,
  resetAntispamStore,
  restoreAntispamLog,
  RULE_IDS,
  scanAntispamRule,
  LIST_IDS,
  listAntispamWordLists,
} from './antispam-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from './workspace-store';

describe('antispam-store (FE-23 MSW)', () => {
  beforeEach(() => {
    resetAntispamStore();
  });

  it('seeds VK + TG rules, stop-word list, and log entries', () => {
    const rules = listAntispamRules({
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
    });
    expect(rules.some((r) => r.id === RULE_IDS.vkActive)).toBe(true);
    expect(rules.some((r) => r.id === RULE_IDS.tgActive)).toBe(true);

    const lists = listAntispamWordLists(PERSONAL_WORKSPACE_ID);
    expect(lists.some((l) => l.id === LIST_IDS.stopWords && l.name === 'Стоп-слова')).toBe(true);

    const { items } = listAntispamLog({ workspaceId: PERSONAL_WORKSPACE_ID });
    expect(items.some((i) => i.id === LOG_IDS.vkDeleted && i.restorable)).toBe(true);
    expect(items.some((i) => i.id === LOG_IDS.tgDeleted && !i.restorable)).toBe(true);
  });

  it('creates rule with black word and seeds a VK history hit', () => {
    const result = createAntispamRule(PERSONAL_WORKSPACE_ID, DEFAULT_PROJECT_ID, {
      name: 'E2E stop',
      channelId: CHANNEL_IDS.vkNews,
      sections: ['wall_comment'],
      config: {
        filters: {
          keywords: { black: ['спам'], white: [], black_list_ids: [], white_list_ids: [] },
        },
      } as never,
    });
    expect('id' in result).toBe(true);
    if ('id' in result) {
      expect(result.status).toBe('draft');
      const { items } = listAntispamLog({
        workspaceId: PERSONAL_WORKSPACE_ID,
        ruleId: result.id,
      });
      expect(items.length).toBeGreaterThanOrEqual(1);
      expect(items[0]?.restorable).toBe(true);
    }
  });

  it('activates and pauses rules', () => {
    const created = createAntispamRule(PERSONAL_WORKSPACE_ID, DEFAULT_PROJECT_ID, {
      name: 'Draft',
      channelId: CHANNEL_IDS.vkNews,
      sections: ['wall_comment'],
    });
    expect('id' in created).toBe(true);
    if (!('id' in created)) return;
    const activated = activateAntispamRule(created.id!);
    expect('status' in activated && activated.status === 'active').toBe(true);
    const paused = pauseAntispamRule(created.id!);
    expect('status' in paused && paused.status === 'paused').toBe(true);
  });

  it('restores VK log and rejects TG restore', () => {
    const ok = restoreAntispamLog(LOG_IDS.vkDeleted, { unban: true });
    expect('state' in ok && ok.state === 'restored').toBe(true);
    const bad = restoreAntispamLog(LOG_IDS.tgDeleted);
    expect('code' in bad && bad.code === 'antispam_restore_unsupported').toBe(true);
  });

  it('scans VK and rejects TG scan', () => {
    const vk = scanAntispamRule(RULE_IDS.vkActive);
    expect('jobId' in vk).toBe(true);
    const tg = scanAntispamRule(RULE_IDS.tgActive);
    expect('code' in tg && tg.code === 'antispam_tg_scan_unsupported').toBe(true);
  });

  it('computes stats hits24h/hits7d from log timestamps', () => {
    const stats = getAntispamRuleStats(RULE_IDS.vkActive);
    expect('hits' in stats).toBe(true);
    if ('hits' in stats) {
      expect(stats.hits).toBeGreaterThanOrEqual(1);
      expect(stats.hits24h).toBeGreaterThanOrEqual(1);
      expect(stats.hits7d).toBeGreaterThanOrEqual(1);
      expect(stats.byReason?.keyword_black).toBeGreaterThanOrEqual(1);
    }
  });

  it('getAntispamRule returns seeded VK rule', () => {
    expect(getAntispamRule(RULE_IDS.vkActive)?.name).toBe('VK комментарии');
  });
});
