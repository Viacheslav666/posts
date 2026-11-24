import { describe, expect, it } from 'vitest';

import { isTargetModeAvailable, type GrabberModeMatrix } from './mode-capabilities';

describe('isTargetModeAvailable', () => {
  it('keeps postponed repost disabled until the matrix says otherwise', () => {
    expect(isTargetModeAvailable('vk_postponed_repost')).toBe(false);
    expect(isTargetModeAvailable('wall_now')).toBe(true);
    const matrix = {
      modes: [{ id: 'vk_postponed_repost', available: true, postponed: true, repost: true }],
    } as GrabberModeMatrix;
    expect(isTargetModeAvailable('vk_postponed_repost', matrix)).toBe(true);
  });
});
