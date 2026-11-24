const ENTITY_MAP: Record<string, string> = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
  nbsp: '\u00A0',
  mdash: '-',
  ndash: '–',
};

/** Decode a subset of HTML entities used in formatting fixtures. */
export function unescapeHtml(input: string): string {
  let out = '';
  let i = 0;
  while (i < input.length) {
    if (input[i] !== '&') {
      out += input[i];
      i += 1;
      continue;
    }
    const semi = input.indexOf(';', i + 1);
    if (semi === -1 || semi - i > 12) {
      out += '&';
      i += 1;
      continue;
    }
    const raw = input.slice(i + 1, semi);
    if (raw[0] === '#') {
      const hex = raw[1] === 'x' || raw[1] === 'X';
      const code = hex ? Number.parseInt(raw.slice(2), 16) : Number.parseInt(raw.slice(1), 10);
      if (Number.isFinite(code)) {
        out += String.fromCodePoint(code);
        i = semi + 1;
        continue;
      }
    } else {
      const named = ENTITY_MAP[raw.toLowerCase()];
      if (named !== undefined) {
        out += named;
        i = semi + 1;
        continue;
      }
    }
    out += '&';
    i += 1;
  }
  return out;
}

function isBrTag(html: string, start: number): number {
  // Returns end index (exclusive) if `<br`, `<br/>`, `<br />` (case-insensitive); else -1.
  if (html[start] !== '<') {
    return -1;
  }
  const slice = html.slice(start, start + 6).toLowerCase();
  if (!slice.startsWith('<br')) {
    return -1;
  }
  let i = start + 3;
  while (i < html.length && (html[i] === ' ' || html[i] === '\t')) {
    i += 1;
  }
  if (i < html.length && html[i] === '/') {
    i += 1;
  }
  if (i < html.length && html[i] === '>') {
    return i + 1;
  }
  return -1;
}

/**
 * Strip tags → plain text (fixture-compatible).
 * `<br/>` / `<br>` become newlines; incomplete tags without `>` are dropped;
 * entities are decoded after tag removal.
 *
 * Linear scan (no nested regex) so CodeQL ReDoS / incomplete-sanitization alerts stay clear.
 */
export function stripTags(html: string): string {
  let out = '';
  let i = 0;
  while (i < html.length) {
    if (html[i] !== '<') {
      out += html[i];
      i += 1;
      continue;
    }
    const next = html[i + 1];
    const looksLikeTag =
      next !== undefined &&
      ((next >= 'a' && next <= 'z') ||
        (next >= 'A' && next <= 'Z') ||
        next === '/' ||
        next === '!');
    if (!looksLikeTag) {
      out += '<';
      i += 1;
      continue;
    }
    const brEnd = isBrTag(html, i);
    if (brEnd !== -1) {
      out += '\n';
      i = brEnd;
      continue;
    }
    // Drop from '<' through the next '>' (or end of string for incomplete tags).
    const close = html.indexOf('>', i + 1);
    i = close === -1 ? html.length : close + 1;
  }
  return unescapeHtml(out);
}

export function hasMarkupTags(html: string): boolean {
  // Matches Python fixture helper: re.search(r"<[a-zA-Z]", html)
  for (let i = 0; i < html.length - 1; i += 1) {
    if (html[i] === '<') {
      const next = html[i + 1];
      if ((next >= 'a' && next <= 'z') || (next >= 'A' && next <= 'Z')) {
        return true;
      }
    }
  }
  return false;
}

/**
 * Unicode code-point length (Python `len(str)`), not UTF-16 `.length`.
 * Formatting fixtures and Telegram entity limits use code points.
 */
export function unicodeLength(text: string): number {
  return Array.from(text).length;
}
