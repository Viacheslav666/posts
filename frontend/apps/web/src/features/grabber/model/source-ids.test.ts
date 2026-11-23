import { describe, expect, it } from 'vitest';

import { isValidSourceId, normalizeSourceIds, sourceIdsToTextarea } from './source-ids';

describe('normalizeSourceIds (TC-GRAB-22)', () => {
  it('splits commas and spaces, strips @, dedupes, normalizes club/public', () => {
    expect(normalizeSourceIds(' -123 ,  club456  @brand_news brand_news ')).toEqual([
      '-123',
      '-456',
      'brand_news',
    ]);
    expect(normalizeSourceIds('public20, https://vk.com/club10, vk.ru/public20/')).toEqual([
      '-20',
      '-10',
    ]);
  });

  it('accepts empty input', () => {
    expect(normalizeSourceIds('')).toEqual([]);
    expect(normalizeSourceIds('   ')).toEqual([]);
  });
});

describe('sourceIds helpers', () => {
  it('round-trips textarea', () => {
    expect(sourceIdsToTextarea(['-1', 'club2'])).toBe('-1, club2');
  });

  it('validates id shapes', () => {
    expect(isValidSourceId('-123')).toBe(true);
    expect(isValidSourceId('club456')).toBe(true);
    expect(isValidSourceId('https://vk.com/public9')).toBe(true);
    expect(isValidSourceId('bad id')).toBe(false);
  });
});
