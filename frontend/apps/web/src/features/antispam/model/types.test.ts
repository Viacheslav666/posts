import { describe, expect, it } from 'vitest';

import {
  isAntispamNetwork,
  sectionsForNetwork,
  ALL_SECTIONS,
  VK_SECTIONS,
  TG_SECTIONS,
  MAX_SECTIONS,
} from './types';

describe('antispam types helpers', () => {
  it('detects antispam networks', () => {
    expect(isAntispamNetwork('vk')).toBe(true);
    expect(isAntispamNetwork('tg')).toBe(true);
    expect(isAntispamNetwork('max')).toBe(true);
    expect(isAntispamNetwork('ok')).toBe(false);
    expect(isAntispamNetwork(undefined)).toBe(false);
  });

  it('covers all section catalogs', () => {
    expect(VK_SECTIONS).toContain('suggested_post');
    expect(TG_SECTIONS).toHaveLength(2);
    expect(MAX_SECTIONS).toContain('max_chat');
    expect(ALL_SECTIONS.length).toBe(
      VK_SECTIONS.length + TG_SECTIONS.length + MAX_SECTIONS.length,
    );
  });

  it('maps network to sections', () => {
    expect(sectionsForNetwork('vk')[0]).toBe('wall_comment');
    expect(sectionsForNetwork('tg')).toEqual(TG_SECTIONS);
    expect(sectionsForNetwork(null)).toEqual([]);
  });
});
