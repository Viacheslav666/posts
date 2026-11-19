import { describe, expect, it } from 'vitest';

import {
  isRepairableStatus,
  networkHintKey,
  normalizeEventSubscriptionStatus,
  toEventSubscriptionUiStatus,
} from './event-subscription';

describe('event-subscription model', () => {
  it('normalizes known statuses', () => {
    expect(normalizeEventSubscriptionStatus('active')).toBe('active');
    expect(normalizeEventSubscriptionStatus('pending')).toBe('pending');
    expect(normalizeEventSubscriptionStatus('failed')).toBe('failed');
    expect(normalizeEventSubscriptionStatus('degraded')).toBe('degraded');
    expect(normalizeEventSubscriptionStatus('disabled')).toBe('disabled');
  });

  it('maps unknown status to unknown', () => {
    expect(normalizeEventSubscriptionStatus('weird')).toBe('unknown');
    expect(normalizeEventSubscriptionStatus(undefined)).toBe('unknown');
  });

  it('maps backend status to UI labels', () => {
    expect(toEventSubscriptionUiStatus('active')).toBe('connected');
    expect(toEventSubscriptionUiStatus('pending')).toBe('pending');
    expect(toEventSubscriptionUiStatus('failed')).toBe('failed');
    expect(toEventSubscriptionUiStatus('degraded')).toBe('failed');
    expect(toEventSubscriptionUiStatus('disabled')).toBe('disabled');
  });

  it('marks failed/pending/disabled as repairable', () => {
    expect(isRepairableStatus('failed')).toBe(true);
    expect(isRepairableStatus('degraded')).toBe(true);
    expect(isRepairableStatus('pending')).toBe(true);
    expect(isRepairableStatus('disabled')).toBe(true);
    expect(isRepairableStatus('active')).toBe(false);
  });

  it('resolves network hint keys', () => {
    expect(networkHintKey('vk')).toBe('vk');
    expect(networkHintKey('tg')).toBe('tg');
    expect(networkHintKey('max')).toBe('max');
    expect(networkHintKey('ok')).toBe('other');
  });
});
