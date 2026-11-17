import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { renderFromCanonicalHtml } from './render';

type FixtureLimits = {
  telegram_max_chars: number;
  max_max_chars: number;
  telegram_caption_max_chars: number;
  exceeds_telegram: boolean;
  exceeds_max: boolean;
  exceeds_telegram_caption: boolean;
};

type FixtureCase = {
  id: string;
  title: string;
  input: { kind: string; html: string };
  expected: {
    telegram: ReturnType<typeof renderFromCanonicalHtml>['telegram'];
    max: ReturnType<typeof renderFromCanonicalHtml>['max'];
    vk: ReturnType<typeof renderFromCanonicalHtml>['vk'];
    ok: ReturnType<typeof renderFromCanonicalHtml>['ok'];
  };
  limits?: FixtureLimits;
};

type FixtureFile = {
  count: number;
  cases: FixtureCase[];
};

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '../../../../../');
const fixturesPath = join(repoRoot, 'docs/api/fixtures/formatting/cases.json');
const fixtures = JSON.parse(readFileSync(fixturesPath, 'utf8')) as FixtureFile;

describe('formatting fixtures (docs/api/fixtures/formatting)', () => {
  it('loads at least 50 cases', () => {
    expect(fixtures.cases.length).toBeGreaterThanOrEqual(50);
    expect(fixtures.count).toBe(fixtures.cases.length);
  });

  for (const fixture of fixtures.cases) {
    it(`${fixture.id}: ${fixture.title}`, () => {
      const actual = renderFromCanonicalHtml(fixture.input.html);

      expect(actual.telegram).toEqual(fixture.expected.telegram);
      expect(actual.max).toEqual(fixture.expected.max);
      expect(actual.vk).toEqual(fixture.expected.vk);
      expect(actual.ok).toEqual(fixture.expected.ok);

      if (fixture.limits) {
        expect(actual.limits).toEqual(fixture.limits);
      }
    });
  }
});
