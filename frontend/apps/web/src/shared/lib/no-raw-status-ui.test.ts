import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

import { describe, expect, it } from 'vitest';

import { RAW_STATUS_ENUMS } from '@/features/posts/model/status-labels';

const WEB_SRC = join(__dirname, '../..');

function walkTsx(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    if (name === 'node_modules' || name === '.next' || name === 'messages') continue;
    const full = join(dir, name);
    const st = statSync(full);
    if (st.isDirectory()) walkTsx(full, out);
    else if (/\.(tsx|ts)$/.test(name) && !name.endsWith('.test.ts') && !name.endsWith('.test.tsx')) {
      out.push(full);
    }
  }
  return out;
}

describe('UI must not show raw English status enums', () => {
  it('does not pass post.status / publication.state as PostStatusBadge label', () => {
    const files = walkTsx(WEB_SRC);
    const offenders: string[] = [];
    const badLabel =
      /PostStatusBadge[\s\S]{0,200}label=\{\s*(?:post|publication|story|item|row)?\.?status\s*\}/;
    const badLabelState =
      /PostStatusBadge[\s\S]{0,200}label=\{\s*(?:publication|pub|item)?\.?state\s*\}/;

    for (const file of files) {
      const src = readFileSync(file, 'utf8');
      if (!src.includes('PostStatusBadge')) continue;
      if (badLabel.test(src) || badLabelState.test(src)) {
        offenders.push(relative(WEB_SRC, file));
      }
    }

    expect(offenders, `Raw status labels in: ${offenders.join(', ')}`).toEqual([]);
  });

  it('does not hardcode English status words as JSX text near badges', () => {
    const files = walkTsx(join(WEB_SRC, 'features'));
    const offenders: string[] = [];
    // e.g. >scheduled< or {'failed'} as visible badge children - skip i18n keys and imports
    const pattern = new RegExp(
      `>(${RAW_STATUS_ENUMS.join('|')})<`,
      'g',
    );

    for (const file of files) {
      const src = readFileSync(file, 'utf8');
      if (pattern.test(src)) {
        offenders.push(relative(WEB_SRC, file));
      }
      pattern.lastIndex = 0;
    }

    expect(offenders, `Hardcoded English statuses in: ${offenders.join(', ')}`).toEqual([]);
  });
});
