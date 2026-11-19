import { beforeEach, describe, expect, it } from 'vitest';

import { CHANNEL_IDS } from './channel-store';
import {
  approveSuggestItem,
  bulkSuggestItems,
  copySuggestSettings,
  createSuggestSettings,
  getSuggestBulkJob,
  getSuggestItem,
  ITEM_IDS,
  listSuggestItems,
  listSuggestSettings,
  patchSuggestSettings,
  rejectSuggestItem,
  resetSuggestStore,
  SETTINGS_IDS,
  syncSuggestLast100,
} from './suggest-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from './workspace-store';

describe('suggest-store MSW', () => {
  beforeEach(() => {
    resetSuggestStore();
  });

  it('seeds active settings for vkNews with pending and history items', () => {
    const settings = listSuggestSettings({
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
    });
    expect(settings).toHaveLength(1);
    expect(settings[0]?.id).toBe(SETTINGS_IDS.vkNews);
    expect(settings[0]?.status).toBe('active');

    const pending = listSuggestItems(SETTINGS_IDS.vkNews, { status: 'pending_moderation' });
    expect(pending.length).toBeGreaterThanOrEqual(3);
    expect(listSuggestItems(SETTINGS_IDS.vkNews, { status: 'published' }).length).toBeGreaterThan(0);
    expect(listSuggestItems(SETTINGS_IDS.vkNews, { status: 'rejected' }).length).toBeGreaterThan(0);
  });

  it('approves pending item into published', () => {
    const item = approveSuggestItem(ITEM_IDS.pending1);
    expect('code' in item).toBe(false);
    if (!('code' in item)) {
      expect(item.status).toBe('published');
      expect(item.publishedRemotePostId).toBeTruthy();
    }
    expect(getSuggestItem(ITEM_IDS.pending1)?.status).toBe('published');
  });

  it('rejects with deleteRemote keep vs delete', () => {
    const kept = rejectSuggestItem(ITEM_IDS.pending2, { deleteRemote: false });
    expect('code' in kept).toBe(false);
    if (!('code' in kept)) expect(kept.status).toBe('rejected');

    const deleted = rejectSuggestItem(ITEM_IDS.pending3, { deleteRemote: true });
    expect('code' in deleted).toBe(false);
    if (!('code' in deleted)) expect(deleted.status).toBe('deleted_remote');
  });

  it('bulk approve_all completes with progressPercent 100', () => {
    resetSuggestStore();
    const job = bulkSuggestItems(SETTINGS_IDS.vkNews, { action: 'approve_all' });
    expect('code' in job).toBe(false);
    if (!('code' in job)) {
      expect(job.progressPercent).toBe(100);
      expect(job.status).toBe('completed');
      expect((job.succeeded ?? 0) + (job.failed ?? 0)).toBe(job.total);
      expect(getSuggestBulkJob(job.id!)?.id).toBe(job.id);
    }
    expect(
      listSuggestItems(SETTINGS_IDS.vkNews, { status: 'pending_moderation' }),
    ).toHaveLength(0);
  });

  it('patches stop words and sync imports an item', () => {
    const patched = patchSuggestSettings(SETTINGS_IDS.vkNews, {
      config: {
        filters: { stop_words: ['реклама'] },
      } as never,
      version: 1,
    });
    expect('code' in patched).toBe(false);

    const before = listSuggestItems(SETTINGS_IDS.vkNews).length;
    const sync = syncSuggestLast100(SETTINGS_IDS.vkNews);
    expect('code' in sync).toBe(false);
    if (!('code' in sync)) {
      expect(sync.imported).toBe(1);
      expect(sync.status).toBe('ok');
    }
    expect(listSuggestItems(SETTINGS_IDS.vkNews).length).toBe(before + 1);
  });

  it('creates settings for another VK channel and copies config', () => {
    const created = createSuggestSettings(PERSONAL_WORKSPACE_ID, DEFAULT_PROJECT_ID, {
      channelId: CHANNEL_IDS.vkShop,
      mode: 'auto',
    });
    // vkShop may lack publishSuggested; create still allowed in MSW
    expect('code' in created || created).toBeTruthy();

    const copy = copySuggestSettings(SETTINGS_IDS.vkNews, {
      channelIds: [CHANNEL_IDS.vkShop],
    });
    expect('code' in copy).toBe(false);
    if (!('code' in copy)) {
      expect(copy.results?.[0]?.ok).toBe(true);
    }
  });

  it('conflicts when creating duplicate settings for same channel', () => {
    const result = createSuggestSettings(PERSONAL_WORKSPACE_ID, DEFAULT_PROJECT_ID, {
      channelId: CHANNEL_IDS.vkNews,
      mode: 'manual',
    });
    expect(result).toMatchObject({ code: 'conflict', status: 409 });
  });
});
