import { describe, expect, it } from 'vitest';

import { renderFromCanonicalHtml } from './render';

describe('formatting performance', () => {
  it('renders 4000-char text well under INP budget slice (median of 10 warmed runs ≤ 50ms)', () => {
    const html = `<b>${'A'.repeat(4000)}</b>`;
    renderFromCanonicalHtml(html);

    const samples: number[] = [];
    for (let i = 0; i < 10; i += 1) {
      const started = performance.now();
      const result = renderFromCanonicalHtml(html);
      samples.push(performance.now() - started);
      expect(result.telegram.plain_length).toBe(4000);
    }

    samples.sort((a, b) => a - b);
    const median = (samples[4]! + samples[5]!) / 2;
    expect(median).toBeLessThan(50);
  });
});
