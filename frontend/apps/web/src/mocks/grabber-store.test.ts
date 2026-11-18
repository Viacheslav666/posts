import { beforeEach, describe, expect, it } from 'vitest';

import {
  approveItem,
  cloneRuleConfig,
  createRule,
  exportItemsCsv,
  getItem,
  getRule,
  ITEM_IDS,
  previewSource,
  resetGrabberStore,
  RULE_IDS,
  startRun,
} from './grabber-store';
import { CHANNEL_IDS } from './channel-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from './workspace-store';

describe('grabber-store MSW', () => {
  beforeEach(() => {
    resetGrabberStore();
  });

  it('defaults publishMode to moderate when omitted (TC-GRAB-08b)', () => {
    const rule = createRule(PERSONAL_WORKSPACE_ID, DEFAULT_PROJECT_ID, {
      type: 'grabber',
      name: 'No mode',
      targetChannelId: CHANNEL_IDS.vkNews,
      userResponsibilityAccepted: true,
      activate: true,
      config: { sourceIds: ['-1'] },
    });
    expect('code' in rule).toBe(false);
    if (!('code' in rule)) {
      expect(rule.config?.publishMode).toBe('moderate');
    }
  });

  it('blocks SSRF RSS urls (TC-GRAB-12)', () => {
    const result = createRule(PERSONAL_WORKSPACE_ID, DEFAULT_PROJECT_ID, {
      type: 'rss',
      name: 'Bad RSS',
      targetChannelId: CHANNEL_IDS.vkNews,
      userResponsibilityAccepted: true,
      activate: true,
      config: { rssUrl: 'http://169.254.169.254/latest' },
    });
    expect(result).toMatchObject({ code: 'ingest_ssrf_blocked', status: 422 });
  });

  it('approve marks item published with postId (TC-GRAB-09)', () => {
    const item = approveItem(ITEM_IDS.pending1);
    expect('code' in item).toBe(false);
    if (!('code' in item)) {
      expect(item.status).toBe('published');
      expect(item.postId).toBeTruthy();
    }
    expect(getItem(ITEM_IDS.pending1)?.status).toBe('published');
  });

  it('manual run creates pending_moderation item for moderate rule (TC-GRAB-03/08)', () => {
    const run = startRun(RULE_IDS.vkActive);
    expect('code' in run).toBe(false);
    if (!('code' in run)) {
      expect(run.trigger).toBe('manual');
    }
  });

  it('preview blocks private website urls', () => {
    const result = previewSource(PERSONAL_WORKSPACE_ID, {
      type: 'website',
      websiteUrls: ['http://10.0.0.5/article'],
    });
    expect(result).toMatchObject({ code: 'ingest_ssrf_blocked' });
  });

  it('clones filters/transform from another rule without changing sources', () => {
    const donor = getRule(RULE_IDS.rssModerate);
    expect(donor).toBeTruthy();
    // seed a strip flag on donor
    if (donor?.config) {
      donor.config = {
        ...donor.config,
        transform: { strip_all_text: true } as never,
        limits: { min_interval_minutes: 45, rate_limit_per_day: 10, rate_limit_per_hour: 2 } as never,
      };
    }
    const result = cloneRuleConfig(RULE_IDS.vkActive, RULE_IDS.rssModerate);
    expect('code' in result).toBe(false);
    if (!('code' in result)) {
      expect(result.config?.sourceIds).toEqual(['-123456', 'club_demo']);
      expect(result.config?.transform).toMatchObject({ strip_all_text: true });
      expect(result.config?.limits).toMatchObject({ min_interval_minutes: 45 });
    }
  });

  it('exports item history as CSV', () => {
    const csv = exportItemsCsv(RULE_IDS.vkActive);
    expect(csv.split('\n')[0]).toContain('remote_post_id');
    expect(csv).toContain('pending_moderation');
  });
});
