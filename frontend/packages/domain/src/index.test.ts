import { describe, expect, it } from 'vitest';
import { isSupportedNetwork } from './index';

describe('isSupportedNetwork', () => {
  it('accepts vk', () => {
    expect(isSupportedNetwork('vk')).toBe(true);
  });
});
