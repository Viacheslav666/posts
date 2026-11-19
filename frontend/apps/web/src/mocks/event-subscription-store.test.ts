import { beforeEach, describe, expect, it } from 'vitest';

import { CHANNEL_IDS } from './channel-store';
import {
  getEventSubscription,
  repairEventSubscription,
  resetEventSubscriptionStore,
} from './event-subscription-store';

describe('event-subscription-store', () => {
  beforeEach(() => {
    resetEventSubscriptionStore();
  });

  it('seeds failed VK subscription for demo channel', () => {
    const sub = getEventSubscription(CHANNEL_IDS.vkNews);
    expect(sub?.status).toBe('failed');
    expect(sub?.lastError).toBeTruthy();
  });

  it('repairs subscription to active', () => {
    const repaired = repairEventSubscription(CHANNEL_IDS.vkNews);
    expect(repaired?.status).toBe('active');
    expect(repaired?.lastError).toBeUndefined();
    expect(getEventSubscription(CHANNEL_IDS.vkNews)?.status).toBe('active');
  });
});
