import { describe, expect, it } from 'vitest';

import { renderFromCanonicalHtml } from './render';

describe('formatting performance', () => {
  it('renders 4000-char text well under INP budget slice (≤ 50ms)', () => {
    const html = `<b>${'A'.repeat(4000)}</b>`;
    const started = performance.now();
    const result = renderFromCanonicalHtml(html);
    const elapsed = performance.now() - started;

    expect(result.telegram.plain_length).toBe(4000);
    expect(elapsed).toBeLessThan(50);
  });
});
