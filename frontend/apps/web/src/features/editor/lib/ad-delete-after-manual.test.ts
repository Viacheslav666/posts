import { describe, expect, it } from 'vitest';

import { shouldClearDeleteAfterOnAdAutoOff } from './ad-delete-after-manual';

describe('shouldClearDeleteAfterOnAdAutoOff', () => {
  it('clears only on transition from ad auto on to off without manual date', () => {
    expect(shouldClearDeleteAfterOnAdAutoOff(true, false, false, true)).toBe(true);
  });

  it('keeps manual deleteAfter when ad auto turns off', () => {
    expect(shouldClearDeleteAfterOnAdAutoOff(true, false, true, true)).toBe(false);
  });

  it('keeps deleteAfter on non-ad posts when editing other options', () => {
    expect(shouldClearDeleteAfterOnAdAutoOff(false, false, true, true)).toBe(false);
  });
});