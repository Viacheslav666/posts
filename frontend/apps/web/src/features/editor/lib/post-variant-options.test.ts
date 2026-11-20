import { describe, expect, it } from 'vitest';

import { sanitizeVariantOptions, validateUnpinAt } from './post-variant-options';

describe('post-variant-options', () => {
  it('strips unpinAt when pin is off', () => {
    const out = sanitizeVariantOptions({
      pin: false,
      unpinAt: '2026-12-01T12:00:00.000Z',
    });
    expect(out.pin).toBe(false);
    expect(out.unpinAt).toBeUndefined();
  });

  it('keeps unpinAt when pin is on', () => {
    const iso = '2026-12-01T12:00:00.000Z';
    const out = sanitizeVariantOptions({ pin: true, unpinAt: iso });
    expect(out.unpinAt).toBe(iso);
  });

  it('validates unpin after publish time', () => {
    const publish = '2026-11-20T10:00:00.000Z';
    expect(validateUnpinAt('2026-11-21T10:00:00.000Z', publish)).toBe(true);
    expect(validateUnpinAt('2026-11-20T09:00:00.000Z', publish)).toBe(false);
    expect(validateUnpinAt(undefined, publish)).toBe(true);
  });
});