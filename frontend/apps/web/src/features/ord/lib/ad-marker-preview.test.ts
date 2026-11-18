import { describe, expect, it } from 'vitest';

import {
  formatAdMarker,
  previewAdPlacement,
  VK_MENU_UNSUPPORTED_WARNING,
} from './ad-marker-preview';

describe('ad-marker-preview', () => {
  const base = {
    bodyText: 'Текст поста https://example.com/offer',
    advertiserName: 'ООО Ромашка',
    inn: '7707083893',
    erid: 'test-erid',
  };

  it('formats marker line', () => {
    expect(formatAdMarker('ООО Ромашка', '7707083893', 'test-erid')).toBe(
      'Реклама. ООО Ромашка. ИНН 7707083893. erid: test-erid',
    );
  });

  it('previews text_start and text_end', () => {
    const start = previewAdPlacement({ ...base, placement: 'text_start' });
    expect(start.text.startsWith('Реклама.')).toBe(true);
    const end = previewAdPlacement({ ...base, placement: 'text_end' });
    expect(end.text.endsWith('erid: test-erid')).toBe(true);
  });

  it('warns for vk_menu modes and falls back to text_end', () => {
    for (const placement of ['vk_menu', 'vk_menu_and_text_end'] as const) {
      const preview = previewAdPlacement({ ...base, placement });
      expect(preview.warnings).toContain(VK_MENU_UNSUPPORTED_WARNING);
      expect(preview.text.endsWith('erid: test-erid')).toBe(true);
    }
  });

  it('injects erid into URLs for click_link modes', () => {
    const preview = previewAdPlacement({ ...base, placement: 'click_link_start' });
    expect(preview.text).toContain('erid=test-erid');
    expect(preview.warnings).toHaveLength(0);
  });
});
