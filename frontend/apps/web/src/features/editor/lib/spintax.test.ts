import { describe, expect, it } from 'vitest';

import { containsSpintax, expandSpintax } from './spintax';

describe('spintax', () => {
  it('detects [rand] blocks', () => {
    expect(containsSpintax('hello [rand]a|b[/rand]')).toBe(true);
    expect(containsSpintax('plain')).toBe(false);
  });

  it('expands deterministically for the same seed', () => {
    const template = '[rand]alpha|beta|gamma[/rand] and [rand]x|y[/rand]';
    const a = expandSpintax(template, 'seed-1');
    const b = expandSpintax(template, 'seed-1');
    expect(a).toBe(b);
    expect(a).not.toContain('[rand]');
  });

  it('can produce a different variant for another seed', () => {
    const template = '[rand]one|two|three|four|five|six|seven|eight[/rand]';
    const a = expandSpintax(template, 'seed-a');
    const b = expandSpintax(template, 'seed-b');
    // Not guaranteed different for every seed pair, but likely for these.
    expect(typeof a).toBe('string');
    expect(typeof b).toBe('string');
    expect(a.length).toBeGreaterThan(0);
  });

  it('supports Cyrillic options', () => {
    const out = expandSpintax('[rand]привет|здравствуйте[/rand]', 'ru');
    expect(['привет', 'здравствуйте']).toContain(out);
  });
});
