const ENTITY_MAP: Record<string, string> = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
  nbsp: '\u00A0',
  mdash: '—',
  ndash: '–',
};

/** Decode a subset of HTML entities used in formatting fixtures. */
export function unescapeHtml(input: string): string {
  return input.replace(/&(#x?[0-9a-f]+|[a-z]+);/gi, (full, raw: string) => {
    if (raw[0] === '#') {
      const hex = raw[1] === 'x' || raw[1] === 'X';
      const code = hex ? Number.parseInt(raw.slice(2), 16) : Number.parseInt(raw.slice(1), 10);
      if (Number.isFinite(code)) {
        return String.fromCodePoint(code);
      }
      return full;
    }
    const named = ENTITY_MAP[raw.toLowerCase()];
    return named ?? full;
  });
}

/**
 * Strip tags → plain text (fixture-compatible).
 * `<br/>` / `<br>` become newlines; entities are decoded after tag removal.
 */
export function stripTags(html: string): string {
  let text = html.replace(/<br\s*\/?>/gi, '\n');
  text = text.replace(/<[^>]+>/g, '');
  return unescapeHtml(text);
}

export function hasMarkupTags(html: string): boolean {
  return /<[a-zA-Z]/.test(html);
}

/**
 * Unicode code-point length (Python `len(str)`), not UTF-16 `.length`.
 * Formatting fixtures and Telegram entity limits use code points.
 */
export function unicodeLength(text: string): number {
  return Array.from(text).length;
}
