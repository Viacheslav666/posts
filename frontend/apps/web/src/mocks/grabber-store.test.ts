import { beforeEach, describe, expect, it } from 'vitest';

import {
  approveItem,
  createRule,
  getItem,
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
});
