import { describe, expect, it } from 'vitest';

import { isKnownLogState, isKnownScanStatus, parseAntispamReason } from './reasons';

describe('antispam reason and status labels', () => {
  it('maps engine reason codes onto translation keys', () => {
    expect(parseAntispamReason('link')).toEqual({ kind: 'key', key: 'links' });
    expect(parseAntispamReason('vk_link')).toEqual({ kind: 'key', key: 'links_vk' });
    expect(parseAntispamReason('from_community')).toEqual({
      kind: 'key',
      key: 'authors_communities',
    });
    expect(parseAntispamReason('keyword:спам')).toEqual({ kind: 'keyword', word: 'спам' });
    expect(parseAntispamReason('attachment:photo')).toEqual({
      kind: 'attachment',
      attachment: 'photo',
    });
  });

  it('leaves unknown codes raw', () => {
    expect(parseAntispamReason('future_code')).toEqual({ kind: 'raw', text: 'future_code' });
  });

  it('recognises journal states from the contract, including restore_failed', () => {
    expect(isKnownLogState('done')).toBe(true);
    expect(isKnownLogState('failed')).toBe(true);
    expect(isKnownLogState('restore_failed')).toBe(true);
    expect(isKnownLogState('pending')).toBe(true);
    expect(isKnownLogState('restored')).toBe(true);
    expect(isKnownLogState('queued')).toBe(false);
  });

  it('recognises scan statuses queued and done', () => {
    expect(isKnownScanStatus('queued')).toBe(true);
    expect(isKnownScanStatus('done')).toBe(true);
    expect(isKnownScanStatus('failed')).toBe(true);
    expect(isKnownScanStatus('running')).toBe(false);
  });
});
