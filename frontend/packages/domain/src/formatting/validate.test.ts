import { describe, expect, it } from 'vitest';

import { hasBlockingValidationErrors, validateCanonicalHtml } from './validate';

describe('validateCanonicalHtml', () => {
  it('blocks when Telegram length exceeds capability', () => {
    const issues = validateCanonicalHtml('T'.repeat(4097), [
      { id: 'tg', maxChars: 4096, formattingMode: 'html' },
    ]);
    expect(hasBlockingValidationErrors(issues)).toBe(true);
    expect(issues.some((i) => i.code === 'limit:max_chars' && i.network === 'tg')).toBe(true);
  });

  it('warns when VK strips formatting', () => {
    const issues = validateCanonicalHtml('<b>x</b>', [
      { id: 'vk', maxChars: null, formattingMode: 'plain' },
    ]);
    expect(hasBlockingValidationErrors(issues)).toBe(false);
    expect(issues.some((i) => i.severity === 'warning' && i.network === 'vk')).toBe(true);
  });
});
