import { describe, expect, it } from 'vitest';

import { isValidInn, normalizeInn } from './inn';

describe('INN validation', () => {
  it('accepts known valid 10-digit INN', () => {
    expect(isValidInn('7707083893')).toBe(true);
    expect(isValidInn('7736050003')).toBe(true);
  });

  it('accepts known valid 12-digit INN', () => {
    expect(isValidInn('500100732259')).toBe(true);
  });

  it('rejects wrong checksum and length', () => {
    expect(isValidInn('7707083890')).toBe(false);
    expect(isValidInn('123')).toBe(false);
    expect(isValidInn('abcdefghij')).toBe(false);
    expect(isValidInn('')).toBe(false);
  });

  it('normalizes non-digits', () => {
    expect(normalizeInn('7707-083-893')).toBe('7707083893');
    expect(isValidInn('7707 083 893')).toBe(true);
  });
});
