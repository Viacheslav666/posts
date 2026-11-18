import { describe, expect, it } from 'vitest';

import { isOptionSupported } from './option-support';

describe('option-support (TC-AD-01)', () => {
  it('enables Donut / co-authors only for VK', () => {
    expect(isOptionSupported('donutDays', ['vk'])).toBe(true);
    expect(isOptionSupported('coOwnerIds', ['vk'])).toBe(true);
    expect(isOptionSupported('donutDays', ['tg'])).toBe(false);
    expect(isOptionSupported('coOwnerIds', ['tg', 'ok'])).toBe(false);
  });

  it('enables inline buttons for TG/Max only', () => {
    expect(isOptionSupported('buttons', ['tg'])).toBe(true);
    expect(isOptionSupported('buttons', ['max'])).toBe(true);
    expect(isOptionSupported('buttons', ['vk'])).toBe(false);
  });

  it('enables poll for VK/OK/TG', () => {
    expect(isOptionSupported('poll', ['vk', 'tg'])).toBe(true);
    expect(isOptionSupported('poll', ['max'])).toBe(false);
  });
});
